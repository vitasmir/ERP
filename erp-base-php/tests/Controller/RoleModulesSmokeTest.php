<?php

// Run with: php tests/Controller/RoleModulesSmokeTest.php
require dirname(__DIR__, 2).'/vendor/autoload.php';

use App\Controller\AdminController;
use App\Service\BackendApiClient;
use Psr\Log\NullLogger;
use Symfony\Component\DependencyInjection\Container;
use Symfony\Component\HttpClient\MockHttpClient;
use Symfony\Component\HttpClient\Response\MockResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\RequestStack;
use Symfony\Component\HttpFoundation\Session\Session;
use Symfony\Component\HttpFoundation\Session\Storage\MockArraySessionStorage;
use Twig\Environment;
use Twig\Loader\FilesystemLoader;
use Twig\TwigFunction;

function check(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

$roleId = '17000000-0000-0000-0000-000000000002';
$permissions = [['roleId' => $roleId, 'moduleKey' => 'sales']];
$calls = 0;
$status = 200;
$stack = new RequestStack();
$backend = new BackendApiClient(new MockHttpClient(
    function (string $method, string $url, array $options) use ($roleId, &$permissions, &$calls, &$status): MockResponse {
        if ($method === 'PUT') {
            ++$calls;
            check($url === 'http://backend/api/v1/roles/matrix', 'Wrong save endpoint.');
            check(in_array('Authorization: Bearer test-token', $options['headers'], true), 'Missing bearer token.');
            if ($status === 200) {
                $permissions = json_decode($options['body'], true)['permissions'];
            }

            return new MockResponse('', ['http_code' => $status]);
        }
        check($method === 'GET', 'Unexpected request method.');
        $data = match ($url) {
            'http://backend/api/v1/roles' => [['id' => $roleId, 'name' => 'Buyer', 'initial' => 'B']],
            'http://backend/api/v1/roles/matrix' => [
                'modules' => [['key' => 'sales', 'name' => 'Sales'], ['key' => 'role_modules', 'name' => 'Role modules']],
                'permissions' => $permissions,
            ],
            default => throw new RuntimeException('Unexpected backend URL.'),
        };

        return new MockResponse(json_encode($data));
    },
), $stack, 'http://backend');
$session = new Session(new MockArraySessionStorage());
$session->set('backendToken', 'test-token');
$twig = new Environment(new FilesystemLoader(dirname(__DIR__, 2).'/templates'), ['strict_variables' => true]);
$twig->addFunction(new TwigFunction('asset', fn (string $path): string => '/'.$path));
$twig->addFunction(new TwigFunction('path', fn (string $route): string => '/apps'));
$container = new Container();
$container->set('twig', $twig);
$controller = new AdminController();
$controller->setContainer($container);
$logger = new NullLogger();

foreach ([['role_modules'], ['sales', 'role_modules'], []] as $selected) {
    $fields = ['action' => 'save-module-permissions'];
    foreach ($selected as $key) {
        $fields['permission_'.$roleId.'_'.$key] = 'on';
    }
    $request = Request::create('/role-modules', 'POST', $fields);
    $request->setSession($session);
    $stack->push($request);
    $response = $controller->roleModules($request, $backend, $logger);
    check(str_starts_with($response->headers->get('Location'), '/role-modules?message='), 'Save must show success.');
    check($permissions === array_map(fn (string $key): array => ['roleId' => $roleId, 'moduleKey' => $key], $selected), 'Selected permissions were not persisted.');
    $stack->pop();

    $request = Request::create('/role-modules');
    $request->setSession($session);
    $stack->push($request);
    $twig->addGlobal('app', (object) ['request' => $request, 'session' => $session]);
    $html = $controller->roleModules($request, $backend, $logger)->getContent();
    foreach (['sales', 'role_modules'] as $key) {
        check((bool) preg_match('/name="permission_'.$roleId.'_'.$key.'"\\s+checked/', $html) === in_array($key, $selected, true), 'Reloaded checkbox state is incorrect.');
    }
    $stack->pop();
}

$request = Request::create('/role-modules', 'POST', [
    'action' => 'save-module-permissions', 'permission_not-a-uuid_sales' => 'on',
]);
$request->setSession($session);
$stack->push($request);
$response = $controller->roleModules($request, $backend, $logger);
check(str_starts_with($response->headers->get('Location'), '/role-modules?error='), 'Malformed ID must show an error.');
check($calls === 3, 'Malformed ID must not reach the backend.');
$request->request->remove('permission_not-a-uuid_sales');
$request->request->set('permission_'.$roleId.'_sales', 'on');
$status = 403;
$response = $controller->roleModules($request, $backend, $logger);
check(str_starts_with($response->headers->get('Location'), '/role-modules?error='), 'Backend refusal must show an error.');
check($permissions === [], 'Rejected save must not change stored permissions.');

echo "Role module smoke tests passed: seeded UUIDs, adding/removing/clearing permissions, reload state and visible failures.\n";
