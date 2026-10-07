<?php

namespace App\Controller;

use App\Service\BackendApiClient;
use App\Service\BackendApiException;
use Psr\Log\LoggerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Contracts\HttpClient\Exception\ExceptionInterface;
use Symfony\Contracts\HttpClient\ResponseInterface;

final class PromoController extends AbstractController
{
    private const STATUSES = ['PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED'];
    private const UUID_PATTERN = '/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/';
    private const DECIMAL_PATTERN = '/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/';

    public function __construct(
        private readonly BackendApiClient $backend,
        private readonly LoggerInterface $logger,
    ) {
    }

    #[Route('/promo', name: 'app_promo', methods: ['GET'])]
    public function index(Request $request): Response
    {
        $campaigns = [];
        $options = ['products' => [], 'suppliers' => []];
        $loadError = null;
        try {
            $campaigns = $this->backend->json('GET', '/api/v1/promo-campaigns');
            $loaded = $this->backend->json('GET', '/api/v1/promo-campaigns/options');
            $options = [
                'products' => is_array($loaded['products'] ?? null) ? $loaded['products'] : [],
                'suppliers' => is_array($loaded['suppliers'] ?? null) ? $loaded['suppliers'] : [],
            ];
        } catch (BackendApiException $exception) {
            $loadError = 'Backend pro promo kampaně není dostupný: '.$exception->getMessage();
        } catch (ExceptionInterface|\UnexpectedValueException $exception) {
            $this->logger->error('Promo campaigns request failed.', ['exception' => $exception]);
            $loadError = 'Backend pro promo kampaně není dostupný: '.$exception->getMessage();
        }

        return $this->render('promo/index.html.twig', [
            'pageTitle' => 'Promo kampaně',
            'breadcrumb' => 'PRODEJ / PROMO',
            'campaigns' => array_values($campaigns),
            'options' => $options,
            'statuses' => self::STATUSES,
            'loadError' => $loadError,
            'actionError' => $request->query->get('error'),
            'message' => $request->query->get('message'),
        ]);
    }

    #[Route('/promo', name: 'app_promo_post', methods: ['POST'])]
    public function post(Request $request): Response
    {
        $input = $request->request->all();
        $action = $input['action'] ?? null;
        if ($action === 'create' || $action === 'edit') {
            return $this->saveCampaign($input, $action === 'edit');
        }

        $id = is_string($input['id'] ?? null) ? $input['id'] : '';
        $status = is_string($input['status'] ?? null) ? $input['status'] : '';
        if (preg_match(self::UUID_PATTERN, $id) !== 1 || !in_array($status, self::STATUSES, true)) {
            return $this->redirectWith('error', 'Neplatná změna stavu.');
        }

        try {
            $response = $this->backend->request('PATCH', '/api/v1/promo-campaigns/'.strtolower($id).'/status', ['status' => $status]);
            if ($response->getStatusCode() === 200) {
                return $this->redirectWith('message', 'Stav kampaně byl změněn.');
            }

            return $this->redirectWith('error', $this->backendError($response, 'Změnu stavu backend odmítl.'));
        } catch (ExceptionInterface $exception) {
            $this->logger->error('Promo campaign status change failed.', ['exception' => $exception]);

            return $this->redirectWith('error', 'Změna stavu se nezdařila. Ověřte stav kampaně před opakováním.');
        }
    }

    /**
     * @param array<string, mixed> $input
     */
    private function saveCampaign(array $input, bool $editing): Response
    {
        $invalid = 'Vyplňte platné údaje kampaně.';
        $text = static fn (string $key): ?string => is_string($input[$key] ?? null) ? $input[$key] : null;

        $name = $text('name');
        $productId = $text('productId');
        $supplierId = $text('supplierId');
        $startsOn = $text('startsOn');
        $endsOn = $text('endsOn');
        $id = $text('id');
        $planned = $text('plannedQuantity');
        if ($name === null || $startsOn === null || $endsOn === null
            || $productId === null || preg_match(self::UUID_PATTERN, $productId) !== 1
            || $supplierId === null || preg_match(self::UUID_PATTERN, $supplierId) !== 1
            || ($editing && ($id === null || preg_match(self::UUID_PATTERN, $id) !== 1))
            || $planned === null || preg_match('/^[+-]?\d+$/', $planned) !== 1
            || strlen(ltrim($planned, '+-0')) > 10
            || (int) $planned > 2147483647 || (int) $planned < -2147483648) {
            return $this->redirectWith('error', $invalid);
        }

        $decimals = [];
        foreach (['regularPrice', 'promoPrice', 'supplierPurchasePrice', 'marketingContribution'] as $key) {
            $value = $text($key);
            if ($value === null || preg_match(self::DECIMAL_PATTERN, $value) !== 1) {
                return $this->redirectWith('error', $invalid);
            }
            $decimals[$key] = $value;
        }

        $payload = [
            'name' => $name,
            'productId' => strtolower($productId),
            'supplierId' => strtolower($supplierId),
            'startsOn' => $startsOn,
            'endsOn' => $endsOn,
            'regularPrice' => $decimals['regularPrice'],
            'promoPrice' => $decimals['promoPrice'],
            'supplierPurchasePrice' => $decimals['supplierPurchasePrice'],
            'plannedQuantity' => (int) $planned,
            'marketingContribution' => $decimals['marketingContribution'],
        ];

        try {
            $response = $this->backend->request(
                $editing ? 'PUT' : 'POST',
                '/api/v1/promo-campaigns'.($editing ? '/'.strtolower((string) $id) : ''),
                $payload,
            );
            if ($response->getStatusCode() === ($editing ? 200 : 201)) {
                return $this->redirectWith('message', $editing ? 'Promo kampaň byla upravena.' : 'Promo kampaň byla přidána.');
            }

            return $this->redirectWith('error', $this->backendError(
                $response,
                $editing ? 'Backend odmítl úpravu kampaně.' : 'Backend odmítl vytvoření kampaně.',
            ));
        } catch (ExceptionInterface $exception) {
            $this->logger->error('Promo campaign save failed.', ['exception' => $exception]);

            return $this->redirectWith('error', 'Kampaň se nepodařilo uložit. Ověřte stav kampaní před opakováním.');
        }
    }

    private function backendError(ResponseInterface $response, string $fallback): string
    {
        $status = $response->getStatusCode();
        if ($status === 401) {
            return 'Přihlášení vypršelo. Přihlaste se znovu.';
        }
        try {
            $body = $response->toArray(false);
            foreach (['detail', 'message'] as $key) {
                if (isset($body[$key]) && is_string($body[$key]) && trim($body[$key]) !== '') {
                    return $body[$key];
                }
            }
        } catch (ExceptionInterface) {
        }

        return $fallback.' (HTTP '.$status.')';
    }

    private function redirectWith(string $parameter, string $message): RedirectResponse
    {
        return new RedirectResponse('/promo?'.http_build_query([$parameter => $message], '', '&', PHP_QUERY_RFC3986));
    }
}
