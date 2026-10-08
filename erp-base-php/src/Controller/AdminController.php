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
use Symfony\Contracts\HttpClient\Exception\TransportExceptionInterface;

final class AdminController extends AbstractController
{
    #[Route('/companies', name: 'admin_companies', methods: ['GET', 'POST'])]
    public function companies(Request $request, BackendApiClient $backend, LoggerInterface $logger): Response
    {
        if ($request->isMethod('POST')) {
            $action = $request->request->getString('action');
            $name = trim($request->request->getString('name'));
            $type = trim($request->request->getString('type'));
            if (!in_array($action, ['create', 'update'], true) || $name === '' || $type === '') {
                return $this->feedback('/companies', 'error', 'Vyplňte platné údaje společnosti.');
            }

            $path = '/api/v1/companies';
            if ($action === 'update') {
                $id = $request->request->getString('id');
                if (!$this->isUuid($id)) {
                    return $this->feedback('/companies', 'error', 'Vyberte platnou společnost.');
                }
                $path .= '/'.$id;
            }

            try {
                $response = $backend->request($action === 'update' ? 'PUT' : 'POST', $path, [
                    'name' => $name,
                    'type' => $type,
                    'currency' => trim($request->request->getString('currency')),
                    'status' => trim($request->request->getString('status')),
                    'color' => trim($request->request->getString('color')),
                ]);
                if ($response->getStatusCode() >= 200 && $response->getStatusCode() < 300) {
                    return $this->feedback('/companies', 'message', $action === 'update'
                        ? 'Společnost byla upravena.' : 'Společnost byla vytvořena.');
                }

                return $this->feedback('/companies', 'error', $response->getStatusCode() === Response::HTTP_CONFLICT
                    ? 'Společnost s tímto názvem již existuje.'
                    : sprintf('Backend změnu společnosti odmítl (HTTP %d).', $response->getStatusCode()));
            } catch (TransportExceptionInterface $exception) {
                $logger->error('Could not save ERP company.', ['exception' => $exception]);

                return $this->feedback('/companies', 'error', 'Backend pro společnosti není dostupný.');
            }
        }

        $companies = [];
        $error = $request->query->get('error');
        try {
            $companies = $backend->json('GET', '/api/v1/companies');
        } catch (BackendApiException|TransportExceptionInterface|\UnexpectedValueException $exception) {
            $logger->error('Could not load ERP companies.', ['exception' => $exception]);
            $error ??= 'Backend pro společnosti není dostupný.';
        }

        return $this->render('admin/companies.html.twig', [
            'companies' => $companies,
            'message' => $request->query->get('message'),
            'error' => $error,
            'pageTitle' => 'Společnosti',
            'breadcrumb' => 'BASE / ENTITIES',
        ]);
    }

    #[Route('/users', name: 'admin_users', methods: ['GET', 'POST'])]
    public function users(Request $request, BackendApiClient $backend, LoggerInterface $logger): Response
    {
        if ($request->isMethod('POST')) {
            $action = $request->request->getString('action');
            $id = $request->request->getString('id');
            if (!in_array($action, ['create', 'update', 'delete'], true)
                || (in_array($action, ['update', 'delete'], true) && !$this->isUuid($id))) {
                return $this->feedback('/users', 'error', 'Vyplňte platné údaje uživatele.');
            }

            try {
                if ($action === 'delete') {
                    $response = $backend->request('DELETE', '/api/v1/users/'.$id);
                } else {
                    $employeeId = $request->request->getString('employeeId');
                    $username = trim($request->request->getString('username'));
                    $fullName = trim($request->request->getString('fullName'));
                    $companyName = trim($request->request->getString('companyName'));
                    $password = $request->request->getString('password');
                    if (!$this->isUuid($employeeId) || $username === '' || $fullName === '' || $companyName === ''
                        || ($action === 'create' && strlen($password) < 10)) {
                        return $this->feedback('/users', 'error', 'Vyplňte platné údaje uživatele.');
                    }

                    $path = '/api/v1/users'.($action === 'update' ? '/'.$id : '');
                    $response = $backend->request($action === 'update' ? 'PUT' : 'POST', $path, [
                        'employeeId' => $employeeId,
                        'fullName' => $fullName,
                        'username' => $username,
                        'password' => $password,
                        'companyName' => $companyName,
                        'status' => $request->request->getString('status'),
                        'color' => $request->request->getString('color'),
                    ]);
                }

                if ($response->getStatusCode() >= 200 && $response->getStatusCode() < 300) {
                    $message = match ($action) {
                        'update' => 'Uživatel byl aktualizován.',
                        'delete' => 'Uživatel byl smazán.',
                        default => 'Uživatel byl přidán.',
                    };

                    return $this->feedback('/users', 'message', $message);
                }

                return $this->feedback('/users', 'error',
                    sprintf('Backend změnu uživatele odmítl (HTTP %d).', $response->getStatusCode()));
            } catch (TransportExceptionInterface $exception) {
                $logger->error('Could not save ERP user.', ['exception' => $exception]);

                return $this->feedback('/users', 'error', 'Backend pro uživatele není dostupný.');
            }
        }

        $users = $employees = $roles = $companies = [];
        $error = $request->query->get('error');
        try {
            $users = $backend->json('GET', '/api/v1/users');
            $employees = $backend->json('GET', '/api/v1/users/employee-options');
            $roles = $backend->json('GET', '/api/v1/roles');
            $companies = $backend->json('GET', '/api/v1/companies');
        } catch (BackendApiException|TransportExceptionInterface|\UnexpectedValueException $exception) {
            $logger->error('Could not load ERP user administration data.', ['exception' => $exception]);
            $error ??= 'Backend pro uživatele není dostupný.';
        }

        foreach ($users as &$user) {
            $user['initials'] = $this->userInitials((string) ($user['fullName'] ?? ''));
            $user['statusLabel'] = match ($user['status'] ?? '') {
                'INVITED' => 'Pozvánka čeká',
                'SUSPENDED' => 'Pozastavený',
                default => 'Aktivní',
            };
            $user['lastAccessLabel'] = $this->lastAccessLabel($user['lastAccessAt'] ?? null);
        }
        unset($user);
        $requestedEmployeeName = '';
        foreach ($employees as $employee) {
            if (($employee['id'] ?? null) === $request->query->getString('employeeId')) {
                $requestedEmployeeName = (string) ($employee['fullName'] ?? '');
                break;
            }
        }

        return $this->render('admin/users.html.twig', [
            'users' => $users,
            'employees' => $employees,
            'roles' => $roles,
            'companies' => $companies,
            'message' => $request->query->get('message'),
            'error' => $error,
            'requestedEmployeeId' => $request->query->getString('employeeId'),
            'requestedEmployeeName' => $requestedEmployeeName,
            'pageTitle' => 'Uživatelé',
            'breadcrumb' => 'BASE / USERS',
        ]);
    }

    #[Route('/roles', name: 'admin_roles', methods: ['GET', 'POST'])]
    public function roles(Request $request, BackendApiClient $backend, LoggerInterface $logger): Response
    {
        if ($request->isMethod('POST')) {
            return $this->saveRoleRequest($request, $backend, $logger);
        }

        return $this->renderRoles($request, $backend, $logger, false);
    }

    #[Route('/role-modules', name: 'admin_role_modules', methods: ['GET', 'POST'])]
    public function roleModules(Request $request, BackendApiClient $backend, LoggerInterface $logger): Response
    {
        if ($request->isMethod('POST')) {
            return $this->saveRoleRequest($request, $backend, $logger);
        }

        return $this->renderRoles($request, $backend, $logger, true);
    }

    #[Route('/settings', name: 'admin_settings', methods: ['GET', 'POST'])]
    public function settings(Request $request, BackendApiClient $backend, LoggerInterface $logger): Response
    {
        if ($request->isMethod('POST')) {
            $fields = [
                'companyName' => trim($request->request->getString('companyName')),
                'companyEmail' => trim($request->request->getString('companyEmail')),
                'currencyCode' => trim($request->request->getString('currencyCode')),
                'timezone' => trim($request->request->getString('timezone')),
            ];
            $integers = [
                'fiscalYearStartMonth' => filter_var($request->request->get('fiscalYearStartMonth'), FILTER_VALIDATE_INT),
                'defaultPaymentTermsDays' => filter_var($request->request->get('defaultPaymentTermsDays'), FILTER_VALIDATE_INT),
            ];
            $decimals = [];
            foreach (['deliveryFee', 'eshopMarginPercent', 'eshopRoundingUnit', 'eshopDefaultVatRate'] as $key) {
                $value = $request->request->getString($key);
                $decimals[$key] = is_numeric($value) ? (float) $value : null;
            }
            if (in_array('', $fields, true) || !filter_var($fields['companyEmail'], FILTER_VALIDATE_EMAIL)
                || $integers['fiscalYearStartMonth'] === false || $integers['fiscalYearStartMonth'] < 1 || $integers['fiscalYearStartMonth'] > 12
                || $integers['defaultPaymentTermsDays'] === false || $integers['defaultPaymentTermsDays'] < 0
                || in_array(null, $decimals, true)
                || $decimals['deliveryFee'] < 0 || $decimals['eshopMarginPercent'] < 0 || $decimals['eshopMarginPercent'] > 99.98
                || !in_array($decimals['eshopRoundingUnit'], [1.0, 10.0, 100.0], true)
                || $decimals['eshopDefaultVatRate'] < 0 || $decimals['eshopDefaultVatRate'] > 100) {
                return $this->feedback('/settings', 'error', 'Zkontrolujte zadané hodnoty nastavení.');
            }

            try {
                $response = $backend->request('PATCH', '/api/v1/settings', $fields + $integers + $decimals);
                if ($response->getStatusCode() >= 200 && $response->getStatusCode() < 300) {
                    return $this->feedback('/settings', 'message', 'Nastavení bylo uloženo.');
                }

                return $this->feedback('/settings', 'error',
                    sprintf('Uložení nastavení backend odmítl (HTTP %d).', $response->getStatusCode()));
            } catch (TransportExceptionInterface $exception) {
                $logger->error('Could not save ERP settings.', ['exception' => $exception]);

                return $this->feedback('/settings', 'error', 'Backend pro nastavení není dostupný.');
            }
        }

        $settings = null;
        $error = $request->query->get('error');
        try {
            $settings = $backend->json('GET', '/api/v1/settings');
        } catch (BackendApiException|TransportExceptionInterface|\UnexpectedValueException $exception) {
            $logger->error('Could not load ERP settings.', ['exception' => $exception]);
            $error ??= 'Backend pro nastavení není dostupný.';
        }

        return $this->render('admin/settings.html.twig', [
            'settings' => $settings,
            'message' => $request->query->get('message'),
            'error' => $error,
            'pageTitle' => 'Nastavení',
            'breadcrumb' => 'BASE / CONFIGURATION',
        ]);
    }

    private function renderRoles(Request $request, BackendApiClient $backend, LoggerInterface $logger, bool $moduleRoles): Response
    {
        $roles = $matrix = [];
        $error = $request->query->get('error');
        try {
            $roles = $backend->json('GET', '/api/v1/roles');
            $matrix = $backend->json('GET', '/api/v1/roles/matrix');
        } catch (BackendApiException|TransportExceptionInterface|\UnexpectedValueException $exception) {
            $logger->error('Could not load ERP roles.', ['exception' => $exception]);
            $error ??= 'Backend pro role není dostupný.';
        }

        return $this->render($moduleRoles ? 'admin/role_modules.html.twig' : 'admin/roles.html.twig', [
            'roles' => $roles,
            'matrix' => $matrix,
            'message' => $request->query->get('message'),
            'error' => $error,
            'pageTitle' => $moduleRoles ? 'Role pro moduly' : 'Role a oprávnění',
            'breadcrumb' => $moduleRoles ? 'BASE / MODULE ACCESS' : 'BASE / ACCESS',
        ]);
    }

    private function saveRoleRequest(Request $request, BackendApiClient $backend, LoggerInterface $logger): RedirectResponse
    {
        $action = $request->request->getString('action');
        try {
            if ($action === 'save-module-permissions') {
                $permissions = [];
                foreach ($request->request->all() as $name => $_value) {
                    if (!str_starts_with((string) $name, 'permission_')) {
                        continue;
                    }
                    $parts = explode('_', substr((string) $name, strlen('permission_')), 2);
                    if (count($parts) !== 2 || !$this->isUuid($parts[0]) || $parts[1] === '') {
                        return $this->feedback('/role-modules', 'error', 'Vyberte platná oprávnění modulů.');
                    }
                    $permissions[] = ['roleId' => $parts[0], 'moduleKey' => $parts[1]];
                }
                $response = $backend->request('PUT', '/api/v1/roles/matrix', ['permissions' => $permissions]);
                if ($response->getStatusCode() >= 200 && $response->getStatusCode() < 300) {
                    return $this->feedback('/role-modules', 'message', 'Oprávnění modulů byla uložena.');
                }

                return $this->feedback('/role-modules', 'error', 'Oprávnění se nepodařilo uložit.');
            }

            $name = trim($request->request->getString('name'));
            $initial = trim($request->request->getString('initial'));
            if (!in_array($action, ['create', 'update'], true) || $name === '' || $initial === ''
                || ($action === 'update' && !$this->isUuid($request->request->getString('id')))) {
                return $this->feedback('/roles', 'error', 'Vyplňte platné údaje role.');
            }
            $path = '/api/v1/roles'.($action === 'update' ? '/'.$request->request->getString('id') : '');
            $response = $backend->request($action === 'update' ? 'PUT' : 'POST', $path, [
                'name' => $name,
                'initial' => $initial,
                'description' => trim($request->request->getString('description')),
                'canRead' => $request->request->has('canRead'),
                'canInsert' => $request->request->has('canInsert'),
                'canEdit' => $request->request->has('canEdit'),
                'canManage' => $request->request->has('canManage'),
                'canDelete' => $request->request->has('canDelete'),
                'color' => trim($request->request->getString('color')),
            ]);
            if ($response->getStatusCode() >= 200 && $response->getStatusCode() < 300) {
                return $this->feedback('/roles', 'message', $action === 'update'
                    ? 'Role byla aktualizována.' : 'Role byla vytvořena.');
            }

            return $this->feedback('/roles', 'error',
                sprintf('Backend změnu role odmítl (HTTP %d).', $response->getStatusCode()));
        } catch (TransportExceptionInterface $exception) {
            $logger->error('Could not save ERP roles.', ['exception' => $exception]);

            return $this->feedback($action === 'save-module-permissions' ? '/role-modules' : '/roles',
                'error', 'Backend změnu odmítl nebo není dostupný.');
        }
    }

    private function feedback(string $path, string $key, string $message): RedirectResponse
    {
        return $this->redirect($path.'?'.http_build_query([$key => $message]));
    }

    private function isUuid(string $value): bool
    {
        return (bool) preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/iD', $value);
    }

    private function userInitials(string $fullName): string
    {
        $words = preg_split('/\s+/', trim($fullName), -1, PREG_SPLIT_NO_EMPTY) ?: [];
        if ($words === []) {
            return '';
        }
        $first = preg_split('//u', $words[0], -1, PREG_SPLIT_NO_EMPTY) ?: [$words[0]];
        $lastWord = $words[array_key_last($words)];
        $last = preg_split('//u', $lastWord, -1, PREG_SPLIT_NO_EMPTY) ?: [$lastWord];

        return strtoupper($first[0].(count($words) > 1 ? $last[0] : ($first[1] ?? '')));
    }

    private function lastAccessLabel(mixed $value): string
    {
        if (!is_string($value) || $value === '') {
            return 'Nikdy';
        }
        try {
            return (new \DateTimeImmutable($value))->format('j. n. Y H:i');
        } catch (\Exception) {
            return $value;
        }
    }
}
