<?php

namespace App\Controller;

use App\Service\BackendApiClient;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

final class ShopController extends ModuleControllerSupport
{
    private const CART = 'eshop.cart';
    private const DELIVERY = 'eshop.delivery';

    #[Route('/eshop', name: 'app_eshop', methods: ['GET', 'POST'])]
    #[Route('/shop', name: 'app_shop', methods: ['GET', 'POST'])]
    public function index(Request $request, BackendApiClient $backend): Response
    {
        if ($request->isMethod('POST')) {
            try {
                return $this->action($request, $backend);
            } catch (\InvalidArgumentException $exception) {
                return $this->feedback('/eshop', $exception->getMessage() ?: 'Zkontrolujte zadané hodnoty.', true);
            } catch (\Exception $exception) {
                $parameters = $request->request->get('action') === 'payment' ? ['checkout' => 'payment'] : [];

                return $this->feedback('/eshop', $parameters
                    ? 'Objednávku se nepodařilo dokončit. Backend požadavek odmítl nebo není dostupný.'
                    : 'Požadavek se nepodařilo dokončit. E-shop není dostupný nebo backend požadavek odmítl.', true, $parameters);
            }
        }

        $data = ['shop' => null, 'error' => $request->query->get('error'),
            'orderCompleted' => $request->query->get('order') === 'completed'];
        try {
            try {
                $backend->request('POST', '/api/v1/website/pages/visit?slug=%2Feshop', null, false)->getStatusCode();
            } catch (\Exception $exception) {
                // Visit tracking must not prevent browsing the public storefront.
            }
            $categories = $backend->json('GET', '/api/v1/catalog/categories/tree', null, false);
            $products = $this->availableProducts($backend);
            $settings = $backend->json('GET', '/api/v1/settings', null, false);
            $selected = $this->categoryFilter($request->query->get('categoryId'));
            $delivery = $request->getSession()->get(self::DELIVERY);
            $paymentOpen = $request->query->get('checkout') === 'payment' && is_array($delivery);
            $visible = array_values(array_filter($products, fn (array $product): bool => $selected === null
                ? ($product['categoryId'] ?? null) !== null : ($product['categoryId'] ?? null) === $selected));
            $lines = [];
            $cartCount = 0;
            $cartTotal = 0;
            $byId = array_column($products, null, 'id');
            foreach ($request->getSession()->get(self::CART, []) as $id => $quantity) {
                $product = $byId[$id] ?? null;
                if ($product === null) {
                    continue;
                }
                $quantity = min($quantity, $product['availableQuantity']);
                if ($quantity <= 0) {
                    continue;
                }
                $lineTotal = $this->priceCents($product['price']) * $quantity;
                $lines[] = ['product' => $product, 'quantity' => $quantity, 'lineTotal' => $this->money($lineTotal)];
                $cartCount += $quantity;
                $cartTotal += $lineTotal;
            }
            $data['shop'] = [
                'categories' => $categories, 'categoryOptions' => $this->categoryOptions($categories, $products),
                'products' => $visible, 'totalProductCount' => count($visible), 'selectedCategoryId' => $selected,
                'cart' => $lines, 'cartCount' => $cartCount, 'cartTotal' => $this->money($cartTotal),
                'checkoutOpen' => $request->query->get('checkout') === 'delivery' || (is_array($delivery) && !$paymentOpen),
                'paymentOpen' => $paymentOpen, 'deliveryFee' => $settings['deliveryFee'] ?? 0, 'delivery' => $delivery,
            ];
        } catch (\Exception $exception) {
            $data['error'] = 'E-shop není dostupný. Katalog se nepodařilo načíst.';
        }

        return $this->render('shop/index.html.twig', $data, new Response(headers: ['Cache-Control' => 'no-store']));
    }

    private function availableProducts(BackendApiClient $backend): array
    {
        $products = $backend->json('GET', '/api/v1/catalog/products', null, false);
        $available = [];
        foreach ($products as $product) {
            if (!($product['active'] ?? false)) {
                continue;
            }
            $stocks = $backend->json('GET', '/api/v1/catalog/products/'.$this->uuid($product['id']).'/availability', null, false);
            $product['availableQuantity'] = max(0, array_sum(array_column($stocks, 'quantity')));
            $available[] = $product;
        }

        return $available;
    }

    private function categoryFilter(?string $value): ?string
    {
        try {
            return $this->optionalUuid($value);
        } catch (\InvalidArgumentException $exception) {
            return null;
        }
    }

    private function categoryOptions(array $categories, array $products, int $depth = 0): array
    {
        $options = [];
        foreach ($categories as $category) {
            $options[] = ['id' => $category['id'], 'name' => $category['name'], 'depth' => $depth,
                'productCount' => count(array_filter($products, fn (array $product): bool => ($product['categoryId'] ?? null) === $category['id']))];
            array_push($options, ...$this->categoryOptions($category['children'] ?? [], $products, $depth + 1));
        }

        return $options;
    }

    private function priceCents(string|int|float $price): int
    {
        return (int) round((float) $price * 100);
    }

    private function money(int $cents): string
    {
        return number_format($cents / 100, 2, '.', '');
    }

    private function action(Request $request, BackendApiClient $backend): Response
    {
        $action = $request->request->get('action');
        if ($action === 'delivery') {
            $delivery = [];
            foreach (['firstName', 'lastName', 'phone', 'street', 'city', 'postalCode'] as $field) {
                $delivery[$field] = trim($request->request->getString($field));
            }
            if (in_array('', $delivery, true) || !preg_match('/^[+0-9 ()-]{9,20}$/D', $delivery['phone'])
                || !preg_match('/^\d{3} ?\d{2}$/D', $delivery['postalCode'])) {
                return $this->feedback('/eshop', 'Vyplňte jméno, telefon a úplnou adresu zákazníka.', true, ['checkout' => 'delivery']);
            }
            $request->getSession()->set(self::DELIVERY, $delivery);

            return $this->redirect('/eshop?checkout=payment#payment-step');
        }
        if ($action === 'payment') {
            return $this->payment($request, $backend);
        }
        $id = $this->uuid($request->request->get('productId'));
        $products = array_column($this->availableProducts($backend), null, 'id');
        if (!isset($products[$id])) {
            throw new \InvalidArgumentException('Produkt není dostupný.');
        }
        $cart = $request->getSession()->get(self::CART, []);
        $current = $cart[$id] ?? 0;
        $quantity = match ($action) {
            'add' => $current + $this->integer($request->request->get('quantity'), 1),
            'increase' => $current + 1,
            'decrease' => $current - 1,
            'set' => $this->integer($request->request->get('quantity'), 1),
            'remove' => 0,
            default => throw new \InvalidArgumentException('Neznámá akce košíku.'),
        };
        $quantity = min($quantity, $products[$id]['availableQuantity']);
        if ($quantity <= 0) {
            unset($cart[$id]);
        } else {
            $cart[$id] = $quantity;
        }
        $request->getSession()->set(self::CART, $cart);
        $category = $this->categoryFilter($request->request->get('categoryId'));

        return $this->redirect('/eshop'.($category === null ? '' : '?categoryId='.$category));
    }

    private function payment(Request $request, BackendApiClient $backend): Response
    {
        $method = trim($request->request->getString('paymentMethod'));
        if (!in_array($method, ['card', 'cod'], true)) {
            return $this->feedback('/eshop', 'Vyberte způsob platby.', true, ['checkout' => 'payment']);
        }
        if ($method === 'card') {
            $number = '';
            foreach (['cardNumber1', 'cardNumber2', 'cardNumber3', 'cardNumber4'] as $field) {
                $number .= trim($request->request->getString($field));
            }
            if (!preg_match('/^\d{16}$/D', $number) || !preg_match('/^(0[1-9]|1[0-2])\/\d{2}$/D', trim($request->request->getString('cardExpiry')))
                || !preg_match('/^\d{3,4}$/D', trim($request->request->getString('cardCvc')))) {
                return $this->feedback('/eshop', 'Zkontrolujte číslo karty, platnost a CVV.', true, ['checkout' => 'payment']);
            }
        }
        $session = $request->getSession();
        $cart = $session->get(self::CART, []);
        if (!$cart) {
            return $this->feedback('/eshop', 'Košík je prázdný.', true);
        }
        $delivery = $session->get(self::DELIVERY);
        if (!is_array($delivery)) {
            return $this->feedback('/eshop', 'Vyplňte údaje zákazníka.', true, ['checkout' => 'delivery']);
        }
        $products = array_column($this->availableProducts($backend), null, 'id');
        $lines = [];
        foreach ($cart as $id => $quantity) {
            if (!isset($products[$id]) || $quantity <= 0 || $quantity > $products[$id]['availableQuantity']) {
                return $this->feedback('/eshop', 'Produkty v košíku již nejsou dostupné v požadovaném množství.', true, ['checkout' => 'payment']);
            }
            $lines[] = ['productId' => $this->uuid($id), 'quantity' => $quantity];
        }
        $today = new \DateTimeImmutable('today');
        $response = $this->mutate($backend, 'POST', '/api/v1/sales/orders/checkout', [
            'customerName' => $delivery['firstName'].' '.$delivery['lastName'],
            'orderDate' => $today->format('Y-m-d'), 'deliveryDate' => $today->modify('+1 day')->format('Y-m-d'),
            'paymentMethod' => $method === 'card' ? 'CARD' : 'CASH', 'lines' => $lines,
        ], false);
        if ($response->getStatusCode() !== Response::HTTP_CREATED) {
            return $this->feedback('/eshop', 'Objednávku se nepodařilo dokončit.', true, ['checkout' => 'payment']);
        }
        $session->remove(self::CART);
        $session->remove(self::DELIVERY);
        $session->remove('eshop.payment');

        return $this->redirect('/eshop?order=completed');
    }
}
