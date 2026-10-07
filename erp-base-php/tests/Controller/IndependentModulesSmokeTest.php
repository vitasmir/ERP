<?php

// Run with: php tests/Controller/IndependentModulesSmokeTest.php
require dirname(__DIR__, 2).'/vendor/autoload.php';

use App\Controller\IndependentModulesController;
use App\Service\BackendApiClient;
use Symfony\Component\DependencyInjection\Container;
use Symfony\Component\HttpClient\MockHttpClient;
use Symfony\Component\HttpClient\Response\MockResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\RequestStack;
use Symfony\Component\HttpFoundation\Session\Session;
use Symfony\Component\HttpFoundation\Session\Storage\MockArraySessionStorage;
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

function fixture(): array
{
    $id = '12345678-1234-1234-1234-123456789abc';

    return [
        'receivables' => 2500, 'overdue' => 500, 'pipeline' => 5000, 'forecast' => 2000,
        'activeCampaignCount' => 1, 'openInvoiceCount' => 1, 'openLeadCount' => 1,
        'pendingApprovalCount' => 1, 'approvedCount' => 0, 'categoryCount' => 1,
        'activeProjectCount' => 1, 'inProgressCount' => 1, 'dueSoonCount' => 1,
        'openTicketCount' => 1, 'highPriorityCount' => 1, 'runningCampaignCount' => 1,
        'plannedCampaignCount' => 1, 'totalLeadCount' => 3, 'totalSpent' => 50,
        'publishedPageCount' => 1, 'draftPageCount' => 1, 'formPageCount' => 0, 'monthlyVisits' => 1234,
        'invoices' => [[
            'id' => $id, 'version' => 0, 'invoiceNumber' => 'INV-1', 'partnerName' => '<script>partner</script>',
            'issueDate' => '2026-10-01', 'dueDate' => '2026-10-30', 'totalAmount' => 2500,
            'paidAmount' => 0, 'outstandingAmount' => 2500, 'status' => 'DRAFT',
            'lines' => [['productId' => null, 'description' => 'Line', 'quantity' => 1, 'unitPrice' => 2500, 'imageUrl' => null]],
        ]],
        'leads' => [[
            'id' => $id, 'name' => 'Lead', 'customerName' => 'Customer', 'expectedRevenue' => 5000,
            'probability' => 20, 'expectedCloseDate' => '2026-10-30', 'stage' => 'NEW',
        ]],
        'campaigns' => [[
            'id' => $id, 'name' => 'Campaign', 'startsOn' => '2026-10-01', 'endsOn' => '2026-10-30',
            'status' => 'PLANNED', 'audience' => 'Customers', 'channel' => 'EMAIL', 'ownerName' => 'Owner',
            'budget' => 100, 'spent' => 50, 'leadCount' => 3, 'plannedStartDate' => '2026-10-01',
        ]],
        'documents' => [[
            'id' => $id, 'title' => 'Document', 'category' => 'Contract', 'ownerName' => 'Owner',
            'referenceCode' => 'DOC-1', 'updatedOn' => '2026-10-01', 'status' => 'PENDING_APPROVAL',
        ]],
        'projects' => [[
            'id' => $id, 'name' => 'Project', 'ownerName' => 'Owner', 'department' => 'IT',
            'dueDate' => '2026-10-30', 'progress' => 50, 'status' => 'IN_PROGRESS',
        ]],
        'tickets' => [[
            'id' => $id, 'ticketNumber' => 'T-1', 'subject' => 'Support', 'requesterName' => 'Customer',
            'assignedTeam' => 'IT', 'priority' => 'HIGH', 'dueAt' => '2026-10-01T12:30:00', 'status' => 'OPEN',
        ]],
        'pages' => [[
            'id' => $id, 'title' => 'Page', 'slug' => '/test-page', 'contentType' => 'CONTENT',
            'ownerName' => 'Owner', 'monthlyVisits' => 1234, 'hasContactForm' => false,
            'status' => 'DRAFT', 'updatedAt' => '2026-10-01T12:30:00', 'content' => '<script>content</script>',
        ]],
    ];
}

/**
 * @param list<array{string, string, int, string, ?array}> $expectations
 */
function controller(Request $request, array $expectations): array
{
    $request->setSession(new Session(new MockArraySessionStorage()));
    $request->getSession()->set('backendToken', 'test-token');
    $stack = new RequestStack();
    $stack->push($request);
    $index = 0;
    $client = new MockHttpClient(function (string $method, string $url, array $options) use ($expectations, &$index): MockResponse {
        check(isset($expectations[$index]), 'Unexpected backend request: '.$method.' '.$url);
        [$expectedMethod, $path, $status, $body, $payload] = $expectations[$index++];
        check($method === $expectedMethod, 'Wrong backend method for '.$path);
        check($url === 'http://backend'.$path, 'Wrong backend path: '.$url.' expected '.$path);
        if ($payload !== null) {
            check(json_decode($options['body'], true) == $payload, 'Wrong payload for '.$path);
        }
        if ($status === 0) {
            throw new Symfony\Component\HttpClient\Exception\TransportException('Backend offline');
        }

        return new MockResponse($body, ['http_code' => $status, 'response_headers' => ['Content-Disposition: attachment; filename=invoice.pdf']]);
    });
    $backend = new BackendApiClient($client, $stack, 'http://backend');
    $routes = new RouteCollection();
    foreach (['dashboard', 'accounting', 'crm', 'documents', 'projects', 'helpdesk', 'marketing', 'website'] as $module) {
        $routes->add('app_'.$module, new Route('/'.$module));
    }
    $routes->add('app_home', new Route('/apps'));
    $routes->add('app_logout', new Route('/logout'));
    $router = new UrlGenerator($routes, new RequestContext());
    $twig = new Environment(new FilesystemLoader(dirname(__DIR__, 2).'/templates'), ['strict_variables' => true]);
    $twig->addFunction(new TwigFunction('asset', fn (string $asset): string => '/'.$asset));
    $twig->addFunction(new TwigFunction('path', fn (string $route): string => $router->generate($route)));
    $twig->addGlobal('app', (object) ['request' => $request, 'session' => $request->getSession()]);
    $container = new Container();
    $container->set('twig', $twig);
    $container->set('router', $router);
    $controller = new IndependentModulesController($backend);
    $controller->setContainer($container);

    return [$controller, static function () use ($expectations, &$index): void {
        check($index === count($expectations), 'Not all expected backend requests were made.');
    }];
}

$data = fixture();
$id = $data['invoices'][0]['id'];
$modules = ['dashboard', 'accounting', 'crm', 'documents', 'projects', 'helpdesk', 'marketing', 'website'];
foreach ($modules as $module) {
    $url = '/'.$module.($module === 'helpdesk' ? '?v=20261001-2' : '');
    $request = Request::create($url);
    $expectations = [['GET', '/api/v1/'.$module.'/overview', 200, json_encode($data), null]];
    if ($module === 'accounting') {
        $expectations[] = ['GET', '/api/v1/companies', 200, '[]', null];
    }
    [$handler, $verify] = controller($request, $expectations);
    $response = $handler->$module($request);
    check($response->getStatusCode() === 200, $module.' GET failed');
    check(str_contains($response->getContent(), '/assets/'.$module.'.css'), $module.' CSS missing');
    check(!str_contains($response->getContent(), '<script>partner</script>'), 'Unescaped backend data');
    $verify();

    foreach ([500, 0] as $status) {
        [$handler, $verify] = controller($request, [['GET', '/api/v1/'.$module.'/overview', $status, 'backend failure', null]]);
        $response = $handler->$module($request);
        check(str_contains($response->getContent(), 'nelze načíst'), $module.' GET error hidden');
        $verify();
    }
}

$invoice = [
    'invoiceNumber' => 'INV-1', 'partnerName' => 'Customer', 'issueDate' => '2026-10-01', 'dueDate' => '2026-10-30',
    'lines' => [['description' => 'Faktura INV-1', 'quantity' => 1, 'unitPrice' => 12.5, 'vatRate' => 0]],
];
$cases = [
    ['accounting', ['id' => $id], 'PATCH', '/api/v1/accounting/invoices/'.$id.'/paid', null],
    ['accounting', ['action' => 'create', 'invoiceNumber' => 'INV-1', 'partnerName' => 'Customer', 'issueDate' => '2026-10-01', 'dueDate' => '2026-10-30', 'totalAmount' => '12.50'], 'POST', '/api/v1/accounting/invoices', $invoice],
    ['accounting', ['action' => 'update', 'id' => $id, 'version' => '0', 'invoiceNumber' => 'INV-1', 'partnerName' => 'Customer', 'issueDate' => '2026-10-01', 'dueDate' => '2026-10-30', 'totalAmount' => '12.50'], 'PUT', '/api/v1/accounting/invoices/'.$id, ['version' => 0, 'invoice' => $invoice]],
    ['accounting', ['action' => 'payment', 'id' => $id, 'amount' => '12.50'], 'PATCH', '/api/v1/accounting/invoices/'.$id.'/payment', ['amount' => '12.5']],
    ['crm', ['id' => $id], 'PATCH', '/api/v1/crm/leads/'.$id.'/won', null],
    ['crm', ['name' => 'Lead', 'customerName' => 'Customer', 'expectedRevenue' => '12.50', 'probability' => '20', 'expectedCloseDate' => '2026-10-30'], 'POST', '/api/v1/crm/leads', ['name' => 'Lead', 'customerName' => 'Customer', 'expectedRevenue' => 12.5, 'probability' => 20, 'expectedCloseDate' => '2026-10-30']],
    ['documents', ['id' => $id], 'PATCH', '/api/v1/documents/'.$id.'/approve', null],
    ['projects', ['id' => $id], 'PATCH', '/api/v1/projects/'.$id.'/complete', null],
    ['helpdesk', ['id' => $id], 'PATCH', '/api/v1/helpdesk/tickets/'.$id.'/resolve', null],
    ['marketing', ['id' => $id], 'PATCH', '/api/v1/marketing/campaigns/'.$id.'/launch', null],
    ['marketing', ['action' => 'complete', 'id' => $id], 'PATCH', '/api/v1/marketing/campaigns/'.$id.'/complete', null],
    ['marketing', ['action' => 'create', 'name' => 'Campaign', 'audience' => 'Customers', 'channel' => 'EMAIL', 'ownerName' => 'Owner', 'budget' => '12.50', 'plannedStartDate' => '2026-10-01'], 'POST', '/api/v1/marketing/campaigns', ['name' => 'Campaign', 'audience' => 'Customers', 'channel' => 'EMAIL', 'ownerName' => 'Owner', 'budget' => '12.50', 'plannedStartDate' => '2026-10-01']],
    ['website', ['action' => 'create', 'title' => 'Page', 'slug' => '/page', 'contentType' => 'CONTENT', 'ownerName' => 'Owner', 'content' => 'Body'], 'POST', '/api/v1/website/pages', ['title' => 'Page', 'slug' => '/page', 'contentType' => 'CONTENT', 'ownerName' => 'Owner', 'content' => 'Body']],
    ['website', ['action' => 'edit', 'id' => $id, 'title' => 'Page', 'slug' => '/page', 'contentType' => 'CONTENT', 'ownerName' => 'Owner', 'content' => 'Body'], 'PUT', '/api/v1/website/pages/'.$id, ['title' => 'Page', 'slug' => '/page', 'contentType' => 'CONTENT', 'ownerName' => 'Owner', 'content' => 'Body']],
    ['website', ['id' => $id], 'PATCH', '/api/v1/website/pages/'.$id.'/publish', null],
    ['website', ['action' => 'delete', 'id' => $id], 'DELETE', '/api/v1/website/pages/'.$id, null],
];
foreach ($cases as [$module, $form, $method, $endpoint, $payload]) {
    foreach ([200, 201, 204, 400, 403, 409, 500, 0] as $status) {
        $request = Request::create('/'.$module, 'POST', $form);
        [$handler, $verify] = controller($request, [[$method, $endpoint, $status, '', $payload]]);
        $response = $handler->$module($request);
        check($response->isRedirect(), 'POST did not redirect');
        $location = $response->headers->get('Location');
        check(str_contains($location, $status >= 200 && $status < 300 ? 'message=' : 'error='), 'Wrong feedback for '.$module.' HTTP '.$status);
        if ($module === 'helpdesk') {
            check(str_contains($location, 'v=20261001-2'), 'Helpdesk version missing');
        }
        $verify();
    }
}

foreach (['accounting', 'crm', 'documents', 'projects', 'helpdesk', 'marketing', 'website'] as $module) {
    $request = Request::create('/'.$module, 'POST', ['id' => 'not-a-uuid']);
    [$handler, $verify] = controller($request, []);
    check(str_contains($handler->$module($request)->headers->get('Location'), 'error='), 'Invalid UUID accepted');
    $verify();
}

foreach ([
    ['crm', ['name' => 'Lead', 'customerName' => 'Customer', 'expectedRevenue' => '12.50', 'probability' => '101', 'expectedCloseDate' => '2026-10-30']],
    ['crm', ['name' => 'Lead', 'customerName' => 'Customer', 'expectedRevenue' => '-1', 'probability' => '20', 'expectedCloseDate' => '2026-10-30']],
    ['crm', ['name' => '', 'customerName' => 'Customer', 'expectedRevenue' => '12.50', 'probability' => '20', 'expectedCloseDate' => '2026-10-30']],
    ['accounting', ['action' => 'update', 'id' => $id, 'version' => '-1', 'invoiceNumber' => 'INV-1', 'partnerName' => 'Customer', 'issueDate' => '2026-10-01', 'dueDate' => '2026-10-30', 'totalAmount' => '12.50']],
    ['accounting', ['action' => 'create', 'invoiceNumber' => 'INV-1', 'partnerName' => 'Customer', 'issueDate' => '2026-02-30', 'dueDate' => '2026-10-30', 'totalAmount' => '12.50']],
    ['accounting', ['action' => 'create', 'invoiceNumber' => 'INV-1', 'partnerName' => 'Customer', 'issueDate' => '2026-10-01', 'dueDate' => '2026-10-30', 'totalAmount' => 'not-a-number']],
    ['website', ['action' => 'create', 'title' => 'Page', 'slug' => '/page', 'contentType' => 'INVALID', 'ownerName' => 'Owner']],
    ['marketing', ['action' => 'create', 'name' => 'Campaign', 'audience' => 'Customers', 'channel' => 'INVALID', 'ownerName' => 'Owner', 'budget' => '12.50', 'plannedStartDate' => '2026-10-01']],
] as [$module, $form]) {
    $request = Request::create('/'.$module, 'POST', $form);
    [$handler, $verify] = controller($request, []);
    check(str_contains($handler->$module($request)->headers->get('Location'), 'error='), $module.' invalid form accepted');
    $verify();
}

foreach (['QUALIFIED', 'NOT_A_STAGE'] as $stage) {
    $request = Request::create('/crm?id='.$id.'&stage='.$stage, 'PUT');
    $expectations = $stage === 'QUALIFIED' ? [['PATCH', '/api/v1/crm/leads/'.$id.'/stage', 200, '{}', ['stage' => $stage]]] : [];
    [$handler, $verify] = controller($request, $expectations);
    check($handler->crm($request)->getStatusCode() === ($stage === 'QUALIFIED' ? 200 : 400), 'CRM stage response incorrect');
    $verify();
}

foreach ([403, 500, 0] as $status) {
    $request = Request::create('/crm?id='.$id.'&stage=QUALIFIED', 'PUT');
    [$handler, $verify] = controller($request, [['PATCH', '/api/v1/crm/leads/'.$id.'/stage', $status, '', ['stage' => 'QUALIFIED']]]);
    $response = $handler->crm($request);
    check($response->getStatusCode() === ($status === 0 ? 503 : $status), 'CRM backend error status lost');
    check(str_contains($response->getContent(), 'error'), 'CRM backend error hidden');
    $verify();
}

$request = Request::create('/accounting?pdf='.$id);
[$handler, $verify] = controller($request, [['GET', '/api/v1/accounting/invoices/'.$id.'/pdf', 200, '%PDF-content', null]]);
$response = $handler->accounting($request);
check($response->getContent() === '%PDF-content', 'PDF bytes lost');
check($response->headers->get('Content-Disposition') === 'attachment; filename=invoice.pdf', 'PDF disposition lost');
$verify();

foreach ([404, 500, 0] as $status) {
    $request = Request::create('/accounting?pdf='.$id);
    [$handler, $verify] = controller($request, [['GET', '/api/v1/accounting/invoices/'.$id.'/pdf', $status, '', null]]);
    $response = $handler->accounting($request);
    check($response->getStatusCode() === ($status === 0 ? 503 : $status), 'PDF failure status lost');
    check(!str_contains($response->headers->get('Content-Type', ''), 'application/pdf'), 'PDF failure returned as PDF');
    $verify();
}

$request = Request::create('/test-page');
[$handler, $verify] = controller($request, [
    ['GET', '/api/v1/website/pages/public?slug=%2Ftest-page', 200, json_encode($data['pages'][0]), null],
    ['POST', '/api/v1/website/pages/visit?slug=%2Ftest-page', 204, '', null],
]);
$response = $handler->publicPage($request, 'test-page');
check(str_contains($response->getContent(), '&lt;script&gt;content&lt;/script&gt;'), 'Public content not escaped');
$verify();

foreach ([404, 500, 0] as $status) {
    $request = Request::create('/test-page');
    [$handler, $verify] = controller($request, [['GET', '/api/v1/website/pages/public?slug=%2Ftest-page', $status, '', null]]);
    $response = $handler->publicPage($request, 'test-page');
    check($response->getStatusCode() === ($status === 404 ? 404 : 503), 'Public page failure status lost');
    $verify();
}

$request = Request::create('/website?edit='.$id);
[$handler, $verify] = controller($request, [['GET', '/api/v1/website/overview', 200, json_encode($data), null]]);
$response = $handler->website($request);
check(str_contains($response->getContent(), 'name="action" value="edit"'), 'Website edit form missing');
check(str_contains($response->getContent(), '&lt;script&gt;content&lt;/script&gt;'), 'Website edit content not escaped');
$verify();

$request = Request::create('/helpdesk?error=Failure');
[$handler, $verify] = controller($request, []);
$response = $handler->helpdesk($request);
check(str_contains($response->headers->get('Location'), 'error=Failure'), 'Helpdesk version redirect lost feedback');
$verify();

echo 'Independent module smoke tests passed: 8 GET views, backend/transport failures, 16 actions × 8 outcomes, form validation, CRM stages, PDF and public website.'.PHP_EOL;
