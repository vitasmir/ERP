<?php

// Run with: php tests/Controller/PlanningRolesSmokeTest.php
require dirname(__DIR__, 2).'/vendor/autoload.php';

use App\Controller\OperationsController;
use App\Service\BackendApiClient;
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

$request = Request::create('/planning');
$session = new Session(new MockArraySessionStorage());
$session->set('roleName', 'admin');
$session->set('backendToken', 'test-token');
$request->setSession($session);
$stack = new RequestStack();
$stack->push($request);
$fixtures = [
    '/api/v1/planning/overview' => ['shifts' => [[
        'id' => '12345678-1234-1234-1234-123456789abc', 'version' => 0,
        'status' => 'DRAFT', 'roleName' => 'Driver', 'department' => 'Office',
        'employeeId' => null, 'employeeName' => null,
        'startAt' => '2026-10-08T08:00', 'endAt' => '2026-10-08T16:00',
    ]]],
    '/api/v1/planning/employees' => [
        ['id' => 'one', 'fullName' => 'Employee One', 'teamName' => 'Team', 'jobTitle' => 'Driver'],
        ['id' => 'two', 'fullName' => 'Employee Two', 'teamName' => 'Team', 'jobTitle' => 'Driver'],
    ],
    '/api/v1/planning/workplaces' => [['name' => 'Office', 'capacity' => 1]],
    '/api/v1/planning/notifications' => [],
    '/api/v1/planning/roles' => ['Driver', 'Planner', 'Buyer', 'R&D <Lead>'],
];
$calls = [];
$roleStatus = 200;
$backend = new BackendApiClient(new MockHttpClient(
    function (string $method, string $url) use ($fixtures, &$calls, &$roleStatus): MockResponse {
        $path = parse_url($url, PHP_URL_PATH);
        check($method === 'GET' && array_key_exists($path, $fixtures), 'Unexpected backend request.');
        $calls[] = $path;

        return new MockResponse(json_encode($fixtures[$path]), [
            'http_code' => $path === '/api/v1/planning/roles' ? $roleStatus : 200,
        ]);
    },
), $stack, 'http://backend');
$twig = new Environment(new FilesystemLoader(dirname(__DIR__, 2).'/templates'), ['strict_variables' => true]);
$twig->addFunction(new TwigFunction('asset', fn (string $path): string => '/'.$path));
$twig->addFunction(new TwigFunction('path', fn (string $route): string => '/apps'));
$twig->addGlobal('app', (object) ['request' => $request, 'session' => $session]);
$container = new Container();
$container->set('twig', $twig);
$controller = new OperationsController();
$controller->setContainer($container);
$html = $controller->index('planning', $request, $backend)->getContent();
check($calls === array_keys($fixtures), 'Planning must load the complete role catalog.');
check(preg_match('/<datalist id="role-options">(.*?)<\\/datalist>/s', $html, $matches) === 1, 'Role datalist missing.');
preg_match_all('/<option value="([^"]*)">/', $matches[1], $options);
check($options[1] === ['Driver', 'Planner', 'Buyer', 'R&amp;D &lt;Lead&gt;'], 'Roles must be complete, unique, trimmed and escaped.');
check(substr_count($html, 'list="role-options"') === 2, 'Create and edit forms must share the role list.');
$roleStatus = 503;
$html = $controller->index('planning', $request, $backend)->getContent();
check(str_contains($html, 'Data se nepodařilo načíst.'), 'Role loading failures must be visible.');

echo "Planning role smoke tests passed: all defined roles, no duplicates, unused roles, escaped names, create/edit forms and loading errors.\n";
