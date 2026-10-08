<?php

namespace App\Controller;

use App\Service\BackendApiClient;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

final class OperationsController extends ModuleControllerSupport
{
    #[Route('/sales', name: 'app_sales', methods: ['GET', 'POST'], defaults: ['module' => 'sales'])]
    #[Route('/purchase', name: 'app_purchase', methods: ['GET', 'POST'], defaults: ['module' => 'purchase'])]
    #[Route('/manufacturing', name: 'app_manufacturing', methods: ['GET', 'POST'], defaults: ['module' => 'manufacturing'])]
    #[Route('/pos', name: 'app_pos', methods: ['GET', 'POST'], defaults: ['module' => 'pos'])]
    #[Route('/planning', name: 'app_planning', methods: ['GET', 'POST'], defaults: ['module' => 'planning'])]
    #[Route('/hr', name: 'app_hr', methods: ['GET', 'POST'], defaults: ['module' => 'hr'])]
    public function index(string $module, Request $request, BackendApiClient $backend): Response
    {
        if ($request->isMethod('POST')) {
            try {
                [$method, $path, $body, $message] = match ($module) {
                    'sales' => $this->sales($request),
                    'purchase' => $this->purchase($request),
                    'manufacturing' => $this->manufacturing($request),
                    'pos' => $this->pos($request),
                    'planning' => $this->planning($request),
                    'hr' => $this->hr($request),
                };
                $this->mutate($backend, $method, $path, $body);
                $parameters = [];
                if ($module === 'hr' && $request->request->get('id') !== null) {
                    $parameters['employeeId'] = $this->uuid($request->request->get('id'));
                }

                return $this->feedback('/'.$module, $message, parameters: $parameters);
            } catch (\InvalidArgumentException $exception) {
                return $this->feedback('/'.$module, match ($module) {
                    'sales' => 'Vyplňte platné údaje objednávky.',
                    'purchase' => 'Neplatný nákupní požadavek.',
                    'manufacturing' => 'Neplatný výrobní příkaz.',
                    'pos' => 'Zvolte platný způsob platby.',
                    'planning' => 'Neplatná směna.',
                    'hr' => 'Neplatný zaměstnanec.',
                }, true);
            } catch (\Exception $exception) {
                return $this->feedback('/'.$module, match ($module) {
                    'sales' => 'Změnu dokumentu backend odmítl nebo není dostupný.',
                    'purchase' => in_array($request->request->get('action'), ['create', 'update'], true)
                        ? 'Objednávku se nepodařilo uložit.' : 'Změnu stavu backend odmítl nebo není dostupný.',
                    'manufacturing' => 'Změnu výroby backend odmítl nebo není dostupný.',
                    'pos' => 'Platbu backend odmítl nebo není dostupný.',
                    default => 'Změnu se nepodařilo uložit. Backend požadavek odmítl nebo není dostupný.',
                }, true);
            }
        }

        $role = $this->normalizedRole($request);
        $admin = in_array($role, ['administrator', 'admin'], true);
        $manageAll = $admin || in_array($role, ['hr', 'personalista', 'planovac', 'planner'], true);
        $data = [
            'overview' => null, 'warehouses' => [], 'products' => [], 'employees' => [],
            'workplaces' => [], 'notifications' => [], 'events' => null, 'roleOptions' => [],
            'availability' => null, 'selectedEmployeeId' => null, 'error' => null,
            'message' => $request->query->get('message'), 'actionError' => $request->query->get('error'),
            'admin' => $admin, 'manageAll' => $manageAll,
            'edit' => $module === 'hr' ? ($admin || in_array($role, ['hr', 'personalista'], true))
                : ($manageAll || in_array($role, ['vedouci tymu', 'team lead'], true)),
        ];
        try {
            $data['overview'] = $backend->json('GET', '/api/v1/'.$module.'/overview');
            if ($module === 'purchase') {
                $data['warehouses'] = $backend->json('GET', '/api/v1/purchase/warehouses');
                $data['products'] = $backend->json('GET', '/api/v1/catalog/products');
            } elseif ($module === 'planning') {
                foreach (['employees', 'workplaces', 'notifications'] as $collection) {
                    $data[$collection] = $backend->json('GET', '/api/v1/planning/'.$collection);
                }
                $data['roleOptions'] = $backend->json('GET', '/api/v1/planning/roles');
                if ($request->query->has('audit')) {
                    $data['events'] = $backend->json('GET', '/api/v1/planning/shifts/'.$this->uuid($request->query->get('audit')).'/events');
                }
            } elseif ($module === 'hr') {
                try {
                    $data['roleOptions'] = $backend->json('GET', '/api/v1/roles');
                } catch (\Exception $exception) {
                    $roles = [];
                    foreach ($data['overview']['employees'] ?? [] as $employee) {
                        $name = $employee['userRoleName'] ?? '';
                        if ($name !== '') {
                            $roles[$name] = ['id' => null, 'name' => $name, 'initial' => strtoupper(substr($name, 0, 1)), 'color' => '#D9ED62'];
                        }
                    }
                    $data['roleOptions'] = array_values($roles);
                }
                if ($request->query->has('employeeId')) {
                    $data['selectedEmployeeId'] = $this->uuid($request->query->get('employeeId'));
                    $data['availability'] = $backend->json('GET', '/api/v1/hr/employees/'.$data['selectedEmployeeId'].'/availability');
                }
            }
        } catch (\InvalidArgumentException $exception) {
            $data['error'] = 'Neplatný identifikátor.';
        } catch (\Exception $exception) {
            $data['error'] = 'Backend pro tento modul není dostupný. Data se nepodařilo načíst.';
        }

        return $this->render($module.'/index.html.twig', $data);
    }

    private function sales(Request $request): array
    {
        $action = $request->request->get('action', 'confirm');
        $path = '/api/v1/sales/orders';
        if ($action === 'create' || $action === 'update') {
            $body = $this->fields($request, ['orderNumber', 'customerName', 'orderDate', 'deliveryDate', 'totalAmount']);

            return [$action === 'create' ? 'POST' : 'PUT', $path.($action === 'update' ? '/'.$this->uuid($request->request->get('id')) : ''), $body,
                $action === 'create' ? 'Objednávka byla vytvořena.' : 'Dokument byl upraven.'];
        }
        $path .= '/'.$this->uuid($request->request->get('id'));
        if ($action === 'delete') {
            return ['DELETE', $path, null, 'Dokument byl smazán.'];
        }
        if ($action !== 'confirm') {
            throw new \InvalidArgumentException();
        }

        return ['PATCH', $path.'/confirm', null, 'Nabídka byla potvrzena jako objednávka.'];
    }

    private function purchase(Request $request): array
    {
        $action = $request->request->get('action', 'order');
        $path = '/api/v1/purchase/orders';
        if ($action === 'create' || $action === 'update') {
            $values = $request->request->all();
            $ids = $values['productId'] ?? null;
            $quantities = $values['quantity'] ?? null;
            $prices = $values['unitPrice'] ?? null;
            if (!is_array($ids) || !is_array($quantities) || !is_array($prices) || !$ids || count($ids) !== count($quantities) || count($ids) !== count($prices)) {
                throw new \InvalidArgumentException();
            }
            $lines = [];
            foreach (array_values($ids) as $index => $id) {
                $lines[] = ['productId' => $this->uuid($id), 'quantity' => $this->integer(array_values($quantities)[$index], 1),
                    'unitPrice' => $this->decimal(array_values($prices)[$index])];
            }
            $body = ['supplierName' => $request->request->get('supplierName'),
                'requestedOn' => $this->date($request->request->get('requestedOn')),
                'expectedDeliveryDate' => $this->date($request->request->get('expectedDeliveryDate')),
                'sourceWarehouseId' => $this->uuid($request->request->get('sourceWarehouseId')),
                'destinationWarehouseId' => $this->uuid($request->request->get('destinationWarehouseId')), 'lines' => $lines];

            return [$action === 'create' ? 'POST' : 'PUT', $path.($action === 'update' ? '/'.$this->uuid($request->request->get('id')) : ''), $body,
                $action === 'create' ? 'Nákupní objednávka byla vytvořena.' : 'Nákupní objednávka byla upravena.'];
        }
        if (!in_array($action, ['order', 'receive'], true)) {
            throw new \InvalidArgumentException();
        }

        return ['PATCH', $path.'/'.$this->uuid($request->request->get('id')).'/'.$action,
            $action === 'receive' ? ['quantity' => $request->request->has('quantity') ? $this->integer($request->request->get('quantity'), 1) : null] : null,
            $action === 'receive' ? 'Zboží bylo přijato na sklad.' : 'Nákupní objednávka byla vystavena.'];
    }

    private function manufacturing(Request $request): array
    {
        $action = $request->request->get('action', 'complete');
        $path = '/api/v1/manufacturing/orders/'.$this->uuid($request->request->get('id'));
        if ($action === 'update') {
            return ['PATCH', $path.'/progress', ['completedQuantity' => $this->integer($request->request->get('completedQuantity'), 0)], 'Počet vyrobených kusů byl upraven.'];
        }
        if ($action !== 'complete') {
            throw new \InvalidArgumentException();
        }

        return ['PATCH', $path.'/complete', null, 'Výrobní příkaz byl dokončen.'];
    }

    private function pos(Request $request): array
    {
        $method = $request->request->get('method');
        if (!in_array($method, ['CARD', 'CASH', 'VOUCHER'], true)) {
            throw new \InvalidArgumentException();
        }

        return ['PATCH', '/api/v1/pos/transactions/'.$this->uuid($request->request->get('id')).'/pay',
            ['method' => $method], 'Platba byla přijata a účtenka uzavřena.'];
    }

    private function planning(Request $request): array
    {
        $action = $request->request->get('action');
        $base = '/api/v1/planning';
        $body = null;
        if ($action === 'create' || $action === 'update') {
            $body = $this->fields($request, ['roleName', 'department', 'startAt', 'endAt']);
            $body['employeeId'] = $this->optionalUuid($request->request->get('employeeId'));
            if ($action === 'update') {
                $body['version'] = $this->integer($request->request->get('version'), 0);
            }
            $method = $action === 'create' ? 'POST' : 'PUT';
            $path = $base.'/shifts'.($action === 'update' ? '/'.$this->uuid($request->request->get('id')) : '');
        } elseif ($action === 'publishPlan') {
            $ids = $request->request->all()['shiftIds'] ?? null;
            if (!is_array($ids) || !$ids) {
                throw new \InvalidArgumentException();
            }
            $body = ['shiftIds' => array_map($this->uuid(...), array_values($ids))];
            $method = 'POST';
            $path = $base.'/publish';
        } elseif ($action === 'workplace') {
            $method = 'POST';
            $path = $base.'/workplaces';
            $body = ['name' => $request->request->get('name'), 'capacity' => $this->integer($request->request->get('capacity'), 1)];
        } elseif (in_array($action, ['read', 'delete', 'publish'], true)) {
            $id = $this->uuid($request->request->get('id'));
            $method = $action === 'delete' ? 'DELETE' : 'PATCH';
            $path = $action === 'read' ? $base.'/notifications/'.$id.'/read' : $base.'/shifts/'.$id.($action === 'publish' ? '/publish' : '');
        } else {
            throw new \InvalidArgumentException();
        }

        return [$method, $path, $body, 'Změna byla uložena.'];
    }

    private function hr(Request $request): array
    {
        $action = $request->request->get('action');
        $base = '/api/v1/hr';
        $body = null;
        if ($action === 'create' || $action === 'update') {
            $body = $this->fields($request, ['fullName', 'jobTitle', 'employmentStartDate']);
            $body['teamName'] = '';
            $body['teamId'] = $this->uuid($request->request->get('teamId'));
            if (($deputy = $this->optionalUuid($request->request->get('deputyEmployeeId'))) !== null) {
                $body['deputyEmployeeId'] = $deputy;
            }
            $method = $action === 'create' ? 'POST' : 'PUT';
            $path = $base.'/employees'.($action === 'update' ? '/'.$this->uuid($request->request->get('id')) : '');
        } elseif (in_array($action, ['createTeam', 'updateTeam', 'deleteTeam'], true)) {
            $method = match ($action) { 'createTeam' => 'POST', 'updateTeam' => 'PUT', default => 'DELETE' };
            $path = $base.'/teams'.($action === 'createTeam' ? '' : '/'.$this->uuid($request->request->get('teamId')));
            $body = $action === 'deleteTeam' ? null : ['name' => $request->request->get('name')];
        } else {
            $path = $base.'/employees/'.$this->uuid($request->request->get('id'));
            $method = 'POST';
            switch ($action) {
                case 'absence':
                    $path .= '/absences';
                    $body = $this->fields($request, ['startAt', 'endAt', 'reason']);
                    break;
                case 'qualification':
                    $path .= '/qualifications';
                    $body = $this->fields($request, ['roleName']);
                    break;
                case 'removeAbsence':
                    $path .= '/absences/'.$this->uuid($request->request->get('absenceId'));
                    $method = 'DELETE';
                    break;
                case 'activate':
                case 'deactivate':
                    $path .= '/'.$action;
                    $method = 'PATCH';
                    break;
                default:
                    throw new \InvalidArgumentException();
            }
        }

        return [$method, $path, $body, 'Změna byla uložena.'];
    }
}
