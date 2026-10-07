<?php

namespace App\Controller;

use App\Service\BackendApiClient;
use App\Service\BackendApiException;
use App\Util\Utf8;
use Psr\Log\LoggerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Contracts\HttpClient\Exception\ExceptionInterface;
use Symfony\Contracts\HttpClient\ResponseInterface;

final class InventoryController extends AbstractController
{
    private const UUID_PATTERN = '/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/';
    private const INT_PATTERN = '/^[+-]?\d+$/';

    public function __construct(
        private readonly BackendApiClient $backend,
        private readonly LoggerInterface $logger,
    ) {
    }

    #[Route('/inventory', name: 'app_inventory', methods: ['GET'])]
    public function index(Request $request): Response
    {
        $overview = null;
        $error = null;
        try {
            $overview = $this->backend->json('GET', '/api/v1/inventory/overview');
        } catch (BackendApiException $exception) {
            $error = 'Backend pro sklad není dostupný: '.$exception->getMessage();
        } catch (ExceptionInterface|\UnexpectedValueException $exception) {
            $this->logger->error('Inventory overview request failed.', ['exception' => $exception]);
            $error = 'Backend pro sklad není dostupný: '.$exception->getMessage();
        }

        [$movements, $historyError, $selectedItemId] = $this->loadMovements($request);

        $view = (string) $request->query->get('view', '');
        $selectedView = in_array($view, ['products', 'history'], true) ? $view : 'stock';
        $items = is_array($overview['items'] ?? null) ? $overview['items'] : [];
        $products = is_array($overview['products'] ?? null) ? $overview['products'] : [];

        $lowStockSkus = [];
        $groups = [];
        foreach ($items as $item) {
            $low = ($item['quantity'] ?? 0) < ($item['reorderLevel'] ?? 0);
            $item['lowStock'] = $low;
            if ($low) {
                $lowStockSkus[(string) ($item['sku'] ?? '')] = true;
            }
            $path = (string) ($item['categoryPath'] ?? '');
            $last = array_key_last($groups);
            if ($last === null || $groups[$last]['path'] !== $path) {
                $groups[] = ['path' => $path, 'items' => []];
                $last = array_key_last($groups);
            }
            $groups[$last]['items'][] = $item;
        }
        foreach ($products as &$product) {
            $product['lowStock'] = isset($lowStockSkus[(string) ($product['sku'] ?? '')]);
        }
        unset($product);

        $warehouses = is_array($products[0]['warehouses'] ?? null) ? $products[0]['warehouses'] : [];

        return $this->render('inventory/index.html.twig', [
            'pageTitle' => 'Sklad',
            'breadcrumb' => 'PROVOZ / SKLAD',
            'overview' => $overview,
            'groups' => $groups,
            'itemCount' => count($items),
            'products' => $products,
            'warehouseOptions' => $warehouses,
            'canEdit' => is_array($overview) && ($overview['canEdit'] ?? false) === true,
            'loadError' => $error,
            'actionError' => $request->query->get('error'),
            'message' => $request->query->get('message'),
            'selectedWarehouse' => $request->query->get('warehouseName'),
            'selectedView' => $selectedView,
            'allItems' => $items,
            'movements' => $movements,
            'historyError' => $historyError,
            'selectedItemId' => $selectedItemId,
        ]);
    }

    #[Route('/inventory', name: 'app_inventory_post', methods: ['POST'])]
    public function post(Request $request): Response
    {
        $input = $request->request->all();
        if (($input['action'] ?? null) === 'order') {
            return $this->saveOrder($input);
        }

        $id = $this->stringOrNull($input['id'] ?? null);
        $action = $this->stringOrNull($input['action'] ?? null);
        $reference = $this->stringOrNull($input['reference'] ?? null);
        $note = $this->stringOrNull($input['note'] ?? null);
        $invalid = 'Zadejte kladné celočíselné množství, doklad, platné minimum a cenu.';

        $quantity = $this->parseInt($input['quantity'] ?? null);
        if ($id === null || preg_match(self::UUID_PATTERN, $id) !== 1 || $quantity === null
            || ($action !== null && !in_array($action, ['receive', 'dispatch'], true))
            || $quantity <= 0 || $reference === null || trim($reference) === '' || Utf8::length($reference) > 120
            || ($note !== null && Utf8::length($note) > 500)) {
            return $this->redirectWith('error', $invalid);
        }

        $dispatch = $action === 'dispatch';
        if ($dispatch) {
            $payload = ['quantity' => $quantity, 'reference' => trim($reference), 'note' => $note];
        } else {
            $minimum = $this->parseInt($input['reorderLevel'] ?? null);
            $cost = $this->stringOrNull($input['unitCost'] ?? null);
            if ($minimum === null || $minimum < 0 || $cost === null || trim($cost) === '') {
                return $this->redirectWith('error', $invalid);
            }
            $cost = str_replace(',', '.', $cost);
            if (preg_match('/^\+?(\d*)(?:\.(\d*))?$/', $cost, $parts) !== 1
                || ($parts[1] === '' && ($parts[2] ?? '') === '')
                || strlen($parts[2] ?? '') > 2
                || strlen(ltrim($parts[1], '0')) > 10) {
                return $this->redirectWith('error', $invalid);
            }
            $payload = [
                'quantity' => $quantity,
                'reorderLevel' => $minimum,
                'unitCost' => $cost,
                'reference' => trim($reference),
                'note' => $note,
            ];
        }

        try {
            $response = $this->backend->request(
                'PATCH',
                '/api/v1/inventory/items/'.strtolower($id).($dispatch ? '/dispatch' : '/receive'),
                $payload,
            );
            if ($response->getStatusCode() === 200) {
                return $this->redirectWith('message', $dispatch ? 'Výdej zásoby byl zaevidován.' : 'Příjem zásoby byl zaevidován.');
            }

            return $this->redirectWith('error', $this->backendError($response, 'Skladový pohyb backend odmítl.'));
        } catch (ExceptionInterface $exception) {
            $this->logger->error('Inventory movement request failed.', ['exception' => $exception]);

            return $this->redirectWith('error', 'Spojení se skladem selhalo. Před opakováním ověřte historii pohybů.');
        }
    }

    /**
     * @param array<string, mixed> $input
     */
    private function saveOrder(array $input): Response
    {
        $productId = $this->stringOrNull($input['productId'] ?? null);
        $locationName = $this->stringOrNull($input['locationName'] ?? null);
        $quantity = $this->parseInt($input['quantity'] ?? null);
        if ($productId === null || preg_match(self::UUID_PATTERN, $productId) !== 1 || $quantity === null
            || $quantity < 0 || $locationName === null || trim($locationName) === '') {
            return $this->redirectWith('error', 'Zadejte nezáporné množství a platný sklad.');
        }

        try {
            $response = $this->backend->request('PATCH', '/api/v1/inventory/orders', [
                'productId' => strtolower($productId),
                'locationName' => $locationName,
                'quantity' => $quantity,
            ]);
            $success = $response->getStatusCode() === 200;
            $message = $success ? 'Objednávka z hlavního skladu byla uložena.'
                : $this->backendError($response, 'Objednávku backend odmítl.');

            return new RedirectResponse('/inventory?'.http_build_query([
                $success ? 'message' : 'error' => $message,
                'warehouseName' => $locationName,
                'view' => 'products',
            ], '', '&', PHP_QUERY_RFC3986));
        } catch (ExceptionInterface $exception) {
            $this->logger->error('Inventory order request failed.', ['exception' => $exception]);

            return $this->redirectWith('error', 'Objednávku se nepodařilo odeslat. Ověřte stav před opakováním.');
        }
    }

    /**
     * @return array{0: ?array<string, mixed>, 1: ?string, 2: ?string}
     */
    private function loadMovements(Request $request): array
    {
        $itemId = $this->stringOrNull($request->query->all()['itemId'] ?? null);
        $pageParameter = $this->stringOrNull($request->query->all()['page'] ?? null);
        $page = 0;
        if ($pageParameter !== null || array_key_exists('page', $request->query->all())) {
            $page = $this->parseInt($pageParameter);
        }
        $invalid = 'Neplatný filtr nebo stránka historie.';
        if ($page === null || $page < 0) {
            return [null, $invalid, null];
        }

        $query = 'page='.$page;
        $selected = null;
        if ($itemId !== null && trim($itemId) !== '') {
            if (preg_match(self::UUID_PATTERN, $itemId) !== 1) {
                return [null, $invalid, null];
            }
            $selected = strtolower($itemId);
            $query .= '&itemId='.$selected;
        }

        try {
            $response = $this->backend->request('GET', '/api/v1/inventory/movements?'.$query);
            if ($response->getStatusCode() !== 200) {
                return [null, 'Historii pohybů se nepodařilo načíst: '.$this->backendError($response, 'Historii pohybů backend odmítl.'), $selected];
            }
            $data = $response->toArray(false);
        } catch (ExceptionInterface|\UnexpectedValueException $exception) {
            $this->logger->error('Inventory movements request failed.', ['exception' => $exception]);

            return [null, 'Historii pohybů se nepodařilo načíst: '.$exception->getMessage(), $selected];
        }

        $zone = new \DateTimeZone('Europe/Prague');
        $items = is_array($data['items'] ?? null) ? $data['items'] : [];
        foreach ($items as &$movement) {
            $created = (string) ($movement['createdAt'] ?? '');
            try {
                $movement['createdAtFormatted'] = (new \DateTimeImmutable($created))->setTimezone($zone)->format('d.m.Y H:i:s');
            } catch (\Exception) {
                $movement['createdAtFormatted'] = $created;
            }
        }
        unset($movement);

        $size = (int) ($data['size'] ?? 0);
        $total = (int) ($data['totalElements'] ?? 0);
        $current = (int) ($data['page'] ?? 0);
        $totalPages = $size > 0 ? intdiv($total + $size - 1, $size) : 0;

        return [[
            'items' => $items,
            'page' => $current,
            'totalElements' => $total,
            'totalPages' => $totalPages,
        ], null, $selected];
    }

    private function backendError(ResponseInterface $response, string $fallback): string
    {
        $status = $response->getStatusCode();
        if ($status === 401) {
            return 'Přihlášení vypršelo. Přihlaste se znovu.';
        }
        if ($status === 403) {
            return 'Nemáte oprávnění k této skladové operaci.';
        }
        $contentType = $response->getHeaders(false)['content-type'][0] ?? '';
        if (str_contains($contentType, 'json')) {
            try {
                $body = $response->toArray(false);
                foreach (['detail', 'message'] as $key) {
                    if (isset($body[$key]) && is_string($body[$key]) && trim($body[$key]) !== '') {
                        return $body[$key];
                    }
                }
            } catch (ExceptionInterface) {
            }
        }

        return $fallback.' (HTTP '.$status.')';
    }

    private function redirectWith(string $parameter, string $message): RedirectResponse
    {
        return new RedirectResponse('/inventory?'.http_build_query([$parameter => $message], '', '&', PHP_QUERY_RFC3986));
    }

    private function stringOrNull(mixed $value): ?string
    {
        return is_string($value) ? $value : null;
    }

    private function parseInt(mixed $value): ?int
    {
        if (!is_string($value) || preg_match(self::INT_PATTERN, $value) !== 1) {
            return null;
        }
        $digits = ltrim(ltrim($value, '+-'), '0');
        if (strlen($digits) > 10) {
            return null;
        }
        $number = (int) $value;
        if ($number > 2147483647 || $number < -2147483648) {
            return null;
        }

        return $number;
    }
}
