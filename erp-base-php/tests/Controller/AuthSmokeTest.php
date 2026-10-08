<?php

// Run with: php tests/Controller/AuthSmokeTest.php
require dirname(__DIR__, 2).'/vendor/autoload.php';

use App\Controller\AuthController;
use App\EventSubscriber\FrontendRequestSubscriber;
use App\Service\BackendApiClient;
use Psr\Log\NullLogger;
use Symfony\Component\DependencyInjection\Container;
use Symfony\Component\HttpClient\MockHttpClient;
use Symfony\Component\HttpClient\Response\MockResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\RequestStack;
use Symfony\Component\HttpFoundation\Session\Session;
use Symfony\Component\HttpFoundation\Session\Storage\MockArraySessionStorage;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\HttpKernel\HttpKernelInterface;
use Symfony\Component\Routing\Generator\UrlGenerator;
use Symfony\Component\Routing\RequestContext;
use Symfony\Component\Routing\Route;
use Symfony\Component\Routing\RouteCollection;
use Twig\Environment;
use Twig\Loader\FilesystemLoader;
use Twig\TwigFunction;

function check(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

$request = Request::create('http://localhost:4201/login', 'POST', [
    'username' => 'test-user',
    'password' => 'test-password',
]);
$session = new Session(new MockArraySessionStorage());
$request->setSession($session);
$session->start();
$oldSessionId = $session->getId();
$stack = new RequestStack();
$stack->push($request);
$calls = 0;
$client = new MockHttpClient(function (string $method, string $url, array $options) use (&$calls): MockResponse {
    ++$calls;
    check($method === 'POST' && $url === 'http://backend/api/v1/auth/login', 'Unexpected login request.');
    check(json_decode($options['body'], true) === [
        'username' => 'test-user', 'password' => 'test-password',
    ], 'Wrong credentials payload.');
    check(!isset($options['auth_bearer']), 'Login must not forward an existing token.');

    return new MockResponse(json_encode([
        'token' => 'test-token', 'fullName' => 'Test User', 'roleName' => 'Test Role',
    ]), ['http_code' => 200]);
});
$backend = new BackendApiClient($client, $stack, 'http://backend');
$routes = new RouteCollection();
$routes->add('app_home', new Route('/apps'));
$routes->add('app_login', new Route('/login'));
$routes->add('app_logout', new Route('/logout'));
$router = new UrlGenerator($routes, new RequestContext());
$twig = new Environment(new FilesystemLoader(dirname(__DIR__, 2).'/templates'), ['strict_variables' => true]);
$twig->addFunction(new TwigFunction('asset', fn (string $asset): string => '/'.$asset));
$twig->addFunction(new TwigFunction('path', fn (string $route): string => $router->generate($route)));
$container = new Container();
$container->set('router', $router);
$container->set('twig', $twig);
$controller = new AuthController();
$controller->setContainer($container);
$logger = new NullLogger();
$startedAt = time();
$response = $controller->login($request, $backend, $logger);
check($calls === 1, 'Login did not call the backend exactly once.');
check($response->getStatusCode() === 302 && $response->headers->get('Location') === '/apps', 'Successful login must redirect to /apps.');
check($session->getId() !== $oldSessionId, 'Login must rotate the session ID.');
check($session->get('backendToken') === 'test-token', 'Token missing from session.');
check($session->get('userName') === 'Test User' && $session->get('roleName') === 'Test Role', 'User details missing.');
check($session->get('expiresAt') >= $startedAt + 8 * 60 * 60, 'Eight-hour session limit missing.');
check($session->get('lastActivityAt') >= $startedAt, 'Login activity timestamp missing.');

$kernel = new class implements HttpKernelInterface {
    public function handle(Request $request, int $type = self::MAIN_REQUEST, bool $catch = true): Symfony\Component\HttpFoundation\Response
    {
        throw new LogicException('Kernel handling is not expected in this test.');
    }
};
$subscriber = new FrontendRequestSubscriber($backend, $logger);
foreach (['active', 'idle', 'expired', 'legacy'] as $scenario) {
    $privateRequest = Request::create('http://localhost:4201/apps');
    $privateRequest->setSession($session);
    $session->set('backendToken', 'test-token');
    $session->set('expiresAt', time() + ($scenario === 'expired' ? -1 : 3600));
    $session->set('lastActivityAt', time() - ($scenario === 'idle' ? 1800 : 60));
    if ($scenario === 'legacy') {
        $session->remove('lastActivityAt');
    }
    $event = new RequestEvent($kernel, $privateRequest, HttpKernelInterface::MAIN_REQUEST);
    $subscriber->onRequest($event);
    if (in_array($scenario, ['idle', 'expired'], true)) {
        check($event->getResponse()?->headers->get('Location') === '/login', $scenario.' session must redirect to login.');
        check(!$session->has('backendToken'), $scenario.' session must discard the token.');
    } else {
        check(!$event->hasResponse(), $scenario.' session should remain authenticated.');
        check($session->get('lastActivityAt') >= time() - 1, 'Activity timestamp must be refreshed.');
    }
}

$request->request->set('password', '');
$response = $controller->login($request, $backend, $logger);
check($response->getStatusCode() === 400 && str_contains($response->getContent(), 'role="alert"'), 'Missing credentials must display an error.');
check($calls === 1, 'Missing credentials must not call the backend.');
$request->request->set('password', 'test-password');
$rejectedBackend = new BackendApiClient(new MockHttpClient(new MockResponse('{}', ['http_code' => 401])), $stack, 'http://backend');
$response = $controller->login($request, $rejectedBackend, $logger);
check($response->getStatusCode() === 401 && str_contains($response->getContent(), 'role="alert"'), 'Rejected credentials must display an error.');

echo "Auth smoke tests passed: login redirect, session rotation, identity, idle/absolute expiry, legacy sessions and invalid credentials.\n";
