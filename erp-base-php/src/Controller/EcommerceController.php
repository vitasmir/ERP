<?php

namespace App\Controller;

use App\Service\BackendApiClient;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

final class EcommerceController extends ModuleControllerSupport
{
    #[Route('/ecommerce', name: 'app_ecommerce', methods: ['GET', 'POST'])]
    public function index(Request $request, BackendApiClient $backend): Response
    {
        if ($request->isMethod('POST')) {
            try {
                return $this->save($request, $backend);
            } catch (\InvalidArgumentException|\JsonException $exception) {
                return $this->feedback('/ecommerce', 'Zkontrolujte zadané hodnoty.', true);
            } catch (\Exception $exception) {
                return $this->feedback('/ecommerce', 'Změnu se nepodařilo uložit. Backend požadavek odmítl nebo není dostupný.', true);
            }
        }

        $data = ['ecommerce' => null, 'selectedCategoryId' => null, 'categoryOptions' => [],
            'eshopMarginPercent' => null, 'eshopDefaultVatRate' => null, 'error' => null,
            'message' => $request->query->get('message'), 'actionError' => $request->query->get('error')];
        try {
            $homepage = $backend->json('GET', '/api/v1/catalog/homepage');
            $settings = $backend->json('GET', '/api/v1/settings');
            $categories = $backend->json('GET', '/api/v1/catalog/categories/tree');
            $products = $backend->json('GET', '/api/v1/catalog/products');
            foreach ($products as &$product) {
                $product['availability'] = $backend->json('GET', '/api/v1/catalog/products/'.$this->uuid($product['id']).'/availability');
            }
            unset($product);
            try {
                $data['selectedCategoryId'] = $this->optionalUuid($request->query->get('categoryId'));
            } catch (\InvalidArgumentException $exception) {
                // Invalid category filters show the complete catalog, as in the Java frontend.
            }
            $data['eshopMarginPercent'] = $settings['eshopMarginPercent'] ?? null;
            $data['eshopDefaultVatRate'] = $settings['eshopDefaultVatRate'] ?? null;
            $data['categoryOptions'] = $this->categoryOptions($categories);
            $data['ecommerce'] = ['homepage' => $homepage, 'categories' => $categories,
                'products' => $data['selectedCategoryId'] === null ? $products : array_values(array_filter(
                    $products, fn (array $product): bool => ($product['categoryId'] ?? null) === $data['selectedCategoryId'],
                ))];
        } catch (\Exception $exception) {
            $data['error'] = 'Backend pro eCommerce není dostupný. Katalog se nepodařilo načíst.';
        }

        return $this->render('ecommerce/index.html.twig', $data);
    }

    private function categoryOptions(array $categories, int $depth = 0): array
    {
        $options = [];
        foreach ($categories as $category) {
            $options[] = $category + ['depth' => $depth];
            array_push($options, ...$this->categoryOptions($category['children'] ?? [], $depth + 1));
        }

        return $options;
    }

    private function save(Request $request, BackendApiClient $backend): Response
    {
        $action = $request->request->get('action');
        $base = '/api/v1/catalog';
        $method = 'POST';
        $body = null;
        $parameters = [];
        $fragment = '';
        switch ($action) {
            case 'homepage':
                $method = 'PUT';
                $path = $base.'/homepage';
                $body = $this->fields($request, ['design', 'headline', 'subheadline']);
                $body['textX'] = $this->decimal($request->request->get('textX'));
                $body['textY'] = $this->decimal($request->request->get('textY'));
                $message = 'Homepage byla uložena.';
                break;
            case 'category':
            case 'updateCategory':
                $method = $action === 'category' ? 'POST' : 'PUT';
                $path = $base.'/categories'.($action === 'category' ? '' : '/'.$this->uuid($request->request->get('categoryId')));
                $body = $this->fields($request, ['name', 'slug']);
                $body += ['parentId' => $this->optionalUuid($request->request->get('parentId')), 'sortOrder' => 10, 'active' => true];
                $message = $action === 'category' ? 'Kategorie byla přidána.' : 'Kategorie byla přejmenována.';
                break;
            case 'deleteCategory':
                $method = 'DELETE';
                $path = $base.'/categories/'.$this->uuid($request->request->get('categoryId'));
                $message = 'Kategorie byla smazána.';
                break;
            case 'product':
                $id = $this->optionalUuid($request->request->get('productId'));
                $method = $id === null ? 'POST' : 'PUT';
                $path = $base.'/products'.($id === null ? '' : '/'.$id);
                $body = $this->fields($request, ['sku', 'name', 'unit', 'description', 'imageUrl']);
                foreach (['price', 'purchasePrice', 'vatRate', 'eshopMarginPercent'] as $field) {
                    $body[$field] = $this->decimal($request->request->get($field), true);
                }
                $body['categoryId'] = $this->optionalUuid($request->request->get('categoryId'));
                $body['active'] = $request->request->get('active') === 'on';
                $saved = $this->mutate($backend, $method, $path, $body)->toArray(false);
                if (isset($saved['categoryId'])) {
                    $parameters['categoryId'] = $this->uuid($saved['categoryId']);
                }
                if (isset($saved['id'])) {
                    $fragment = '#product-'.$this->uuid($saved['id']);
                }

                return $this->feedback('/ecommerce', 'Produkt byl uložen.', parameters: $parameters, fragment: $fragment);
            case 'import':
                $body = json_decode($request->request->getString('products'), true, 512, JSON_THROW_ON_ERROR);
                if (!is_array($body) || !array_is_list($body)) {
                    throw new \InvalidArgumentException();
                }
                foreach ($body as $product) {
                    if (!is_array($product)) {
                        throw new \InvalidArgumentException();
                    }
                }
                $path = $base.'/products/import';
                $message = 'Produkty byly naimportovány.';
                break;
            case 'estimate':
                $estimate = $backend->json('POST', $base.'/delivery-estimates', [
                    'productId' => $this->uuid($request->request->get('productId')),
                    'quantity' => $this->integer($request->request->get('quantity'), 1),
                    'postalCode' => $request->request->get('postalCode'), 'method' => $request->request->get('method'),
                ]);
                $available = ($estimate['available'] ?? false) === true;

                return $this->feedback('/ecommerce', $available
                    ? ($estimate['label'] ?? '').': doručení do '.($estimate['estimatedDate'] ?? '')
                    : ($estimate['reason'] ?? 'Doručení není dostupné.'), !$available);
            case 'toggleProduct':
            case 'addImage':
            case 'activateImage':
            case 'deleteImage':
            case 'removeFromCategory':
            case 'deleteProduct':
                $path = $base.'/products/'.$this->uuid($request->request->get('productId'));
                switch ($action) {
                    case 'toggleProduct':
                        $method = 'PUT';
                        $path .= '/active';
                        $body = ['active' => $request->request->get('active') === 'on'];
                        $message = 'Stav produktu byl změněn.';
                        break;
                    case 'addImage':
                        $path .= '/images';
                        $body = ['imageUrl' => $request->request->get('imageUrl'), 'active' => $request->request->get('active') === 'on'];
                        $message = 'Obrázek byl přidán.';
                        break;
                    case 'activateImage':
                    case 'deleteImage':
                        $path .= '/images/'.$this->uuid($request->request->get('imageId'));
                        $method = $action === 'activateImage' ? 'PUT' : 'DELETE';
                        $path .= $action === 'activateImage' ? '/active' : '';
                        $message = $action === 'activateImage' ? 'Aktivní obrázek byl změněn.' : 'Obrázek byl odstraněn.';
                        break;
                    case 'removeFromCategory':
                        $path .= '/category';
                        $method = 'PUT';
                        $message = 'Produkt byl odebrán z kategorie.';
                        break;
                    default:
                        $method = 'DELETE';
                        $message = 'Produkt byl smazán.';
                }
                break;
            default:
                return $this->feedback('/ecommerce', 'Neznámá eCommerce akce.', true);
        }
        $this->mutate($backend, $method, $path, $body);

        return $this->feedback('/ecommerce', $message);
    }
}
