<?php

namespace App\Controller;

use App\Service\BackendApiClient;
use App\Util\Utf8;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

final class IndependentModulesController extends AbstractController
{
    private const HELP_VERSION = '20261001-2';
    private const TITLES = [
        'dashboard' => 'Dashboard', 'accounting' => 'Účetnictví', 'crm' => 'CRM pipeline',
        'documents' => 'Dokumenty', 'projects' => 'Projekty', 'helpdesk' => 'Helpdesk',
        'marketing' => 'Marketing', 'website' => 'Web',
    ];

    public function __construct(private readonly BackendApiClient $backend)
    {
    }

    #[Route('/dashboard', name: 'app_dashboard', methods: ['GET'])]
    public function dashboard(Request $request): Response
    {
        return $this->overview($request, 'dashboard');
    }

    #[Route('/accounting', name: 'app_accounting', methods: ['GET', 'POST'])]
    public function accounting(Request $request): Response
    {
        if ($request->isMethod('GET')) {
            $pdf = $request->query->get('pdf', '');
            if ($pdf !== '') {
                return $this->invoicePdf($pdf);
            }

            return $this->overview($request, 'accounting');
        }

        return $this->action('accounting', function () use ($request): array {
            $action = $request->request->get('action');
            $path = '/api/v1/accounting/invoices';
            if ($action === 'create' || $action === 'update') {
                $number = $this->text($request, 'invoiceNumber', 40);
                $invoice = [
                    'invoiceNumber' => $number,
                    'partnerName' => $this->text($request, 'partnerName', 160),
                    'issueDate' => $this->date($request, 'issueDate'),
                    'dueDate' => $this->date($request, 'dueDate'),
                    'lines' => [[
                        'description' => 'Faktura '.$number, 'quantity' => 1,
                        'unitPrice' => $this->decimal($request, 'totalAmount', 0.01), 'vatRate' => 0,
                    ]],
                ];
                if ($action === 'create') {
                    return ['POST', $path, $invoice, 'Faktura byla uložena.'];
                }

                return ['PUT', $path.'/'.$this->id($request), [
                    'version' => $this->integer($request, 'version', 0),
                    'invoice' => $invoice,
                ], 'Faktura byla uložena.'];
            }
            $path .= '/'.$this->id($request);
            if ($action === 'payment') {
                $amount = $this->decimal($request, 'amount', 0.01);

                return ['PATCH', $path.'/payment', ['amount' => (string) $amount], 'Úhrada byla uložena.'];
            }

            return ['PATCH', $path.'/paid', null, 'Faktura byla označena jako uhrazená.'];
        });
    }

    #[Route('/crm', name: 'app_crm', methods: ['GET', 'POST', 'PUT'])]
    public function crm(Request $request): Response
    {
        if ($request->isMethod('GET')) {
            return $this->overview($request, 'crm');
        }
        if ($request->isMethod('PUT')) {
            try {
                $id = $this->id($request, true);
                $stage = $request->query->get('stage');
                if (!in_array($stage, ['NEW', 'QUALIFIED', 'PROPOSAL', 'WON'], true)) {
                    throw new \InvalidArgumentException('Neplatná fáze příležitosti.');
                }
                $response = $this->backend->request('PATCH', '/api/v1/crm/leads/'.$id.'/stage', ['stage' => $stage]);
                $status = $response->getStatusCode();
                $body = $response->getContent(false);
                if ($status < 200 || $status >= 300) {
                    return $this->json(['error' => 'Změnu fáze backend odmítl (HTTP '.$status.').'], $status);
                }

                return new Response($body, $status, ['Content-Type' => 'application/json']);
            } catch (\InvalidArgumentException $exception) {
                return $this->json(['error' => $exception->getMessage()], 400);
            } catch (\Throwable) {
                return $this->json(['error' => 'Změnu fáze se nepodařilo uložit. Backend není dostupný.'], 503);
            }
        }

        return $this->action('crm', function () use ($request): array {
            if ($request->request->has('id')) {
                return ['PATCH', '/api/v1/crm/leads/'.$this->id($request).'/won', null, 'Příležitost byla označena jako vyhraná.'];
            }

            return ['POST', '/api/v1/crm/leads', [
                'name' => $this->text($request, 'name', 200),
                'customerName' => $this->text($request, 'customerName', 200),
                'expectedRevenue' => $this->decimal($request, 'expectedRevenue', 0),
                'probability' => $this->integer($request, 'probability', 0, 100),
                'expectedCloseDate' => $this->date($request, 'expectedCloseDate'),
            ], 'Příležitost byla vytvořena.'];
        });
    }

    #[Route('/documents', name: 'app_documents', methods: ['GET', 'POST'])]
    public function documents(Request $request): Response
    {
        return $request->isMethod('GET') ? $this->overview($request, 'documents')
            : $this->action('documents', fn (): array => [
                'PATCH', '/api/v1/documents/'.$this->id($request).'/approve', null, 'Dokument byl schválen.',
            ]);
    }

    #[Route('/projects', name: 'app_projects', methods: ['GET', 'POST'])]
    public function projects(Request $request): Response
    {
        return $request->isMethod('GET') ? $this->overview($request, 'projects')
            : $this->action('projects', fn (): array => [
                'PATCH', '/api/v1/projects/'.$this->id($request).'/complete', null, 'Projekt byl označen jako dokončený.',
            ]);
    }

    #[Route('/helpdesk', name: 'app_helpdesk', methods: ['GET', 'POST'])]
    public function helpdesk(Request $request): Response
    {
        if ($request->isMethod('GET')) {
            if ($request->query->get('v') !== self::HELP_VERSION) {
                return $this->redirectToRoute('app_helpdesk', array_merge($request->query->all(), ['v' => self::HELP_VERSION]));
            }

            return $this->overview($request, 'helpdesk');
        }

        return $this->action('helpdesk', fn (): array => [
            'PATCH', '/api/v1/helpdesk/tickets/'.$this->id($request).'/resolve', null, 'Požadavek byl označen jako vyřešený.',
        ]);
    }

    #[Route('/marketing', name: 'app_marketing', methods: ['GET', 'POST'])]
    public function marketing(Request $request): Response
    {
        if ($request->isMethod('GET')) {
            return $this->overview($request, 'marketing');
        }

        return $this->action('marketing', function () use ($request): array {
            $action = $request->request->get('action');
            if ($action === 'create') {
                $channel = $this->text($request, 'channel');
                if (!in_array($channel, ['EMAIL', 'SOCIAL', 'EVENT'], true)) {
                    throw new \InvalidArgumentException('Neplatný kanál kampaně.');
                }

                return ['POST', '/api/v1/marketing/campaigns', [
                    'name' => $this->text($request, 'name'), 'audience' => $this->text($request, 'audience'),
                    'channel' => $channel, 'ownerName' => $this->text($request, 'ownerName'),
                    'budget' => $this->decimalText($request, 'budget'),
                    'plannedStartDate' => $this->date($request, 'plannedStartDate'),
                ], 'Kampaň byla vytvořena.'];
            }
            $operation = $action === 'complete' ? 'complete' : 'launch';

            return ['PATCH', '/api/v1/marketing/campaigns/'.$this->id($request).'/'.$operation, null,
                $operation === 'complete' ? 'Kampaň byla dokončena.' : 'Kampaň byla spuštěna.'];
        });
    }

    #[Route('/website', name: 'app_website', methods: ['GET', 'POST'])]
    public function website(Request $request): Response
    {
        if ($request->isMethod('GET')) {
            return $this->overview($request, 'website');
        }

        return $this->action('website', function () use ($request): array {
            $action = $request->request->get('action');
            $path = '/api/v1/website/pages';
            if ($action === 'create' || $action === 'edit') {
                $type = $this->text($request, 'contentType');
                if (!in_array($type, ['CONTENT', 'LANDING', 'CATALOG', 'CAMPAIGN'], true)) {
                    throw new \InvalidArgumentException('Neplatný typ stránky.');
                }
                $body = [
                    'title' => $this->text($request, 'title'), 'slug' => $this->text($request, 'slug'),
                    'contentType' => $type, 'ownerName' => $this->text($request, 'ownerName'),
                    'content' => $request->request->get('content', ''),
                ];

                return $action === 'create'
                    ? ['POST', $path, $body, 'Stránka byla vytvořena.']
                    : ['PUT', $path.'/'.$this->id($request), $body, 'Stránka byla upravena.'];
            }
            $path .= '/'.$this->id($request);

            return $action === 'delete' ? ['DELETE', $path, null, 'Stránka byla smazána.']
                : ['PATCH', $path.'/publish', null, 'Stránka byla publikována.'];
        });
    }

    #[Route('/{slug}', name: 'app_website_public', requirements: ['slug' => '.+'], methods: ['GET'], priority: -100)]
    public function publicPage(Request $request, string $slug): Response
    {
        $path = '/'.$slug;
        if (in_array($path, [
            '/apps', '/dashboard', '/roles', '/role-modules', '/companies', '/settings', '/users', '/accounting',
            '/crm', '/documents', '/ecommerce', '/helpdesk', '/hr', '/inventory', '/manufacturing', '/marketing',
            '/planning', '/pos', '/projects', '/promo', '/purchase', '/sales', '/website', '/login', '/logout', '/shop', '/eshop',
        ], true) || str_starts_with($path, '/assets/') || str_ends_with($path, '.jsp')) {
            throw $this->createNotFoundException();
        }
        try {
            $response = $this->backend->request('GET', '/api/v1/website/pages/public?'.http_build_query(['slug' => $path]), null, false);
            if ($response->getStatusCode() === 404) {
                return $this->render('website/public.html.twig', ['page' => null, 'error' => 'Stránka nebyla nalezena.', 'pageTitle' => 'Stránka nenalezena'], new Response('', 404));
            }
            if ($response->getStatusCode() < 200 || $response->getStatusCode() >= 300) {
                throw new \RuntimeException();
            }
            $page = $response->toArray(false);
        } catch (\Throwable) {
            return $this->render('website/public.html.twig', ['page' => null, 'error' => 'Stránku nelze načíst. Backend není dostupný.', 'pageTitle' => 'Web'], new Response('', 503));
        }
        $error = null;
        try {
            $visit = $this->backend->request('POST', '/api/v1/website/pages/visit?'.http_build_query(['slug' => $path]), null, false);
            if ($visit->getStatusCode() < 200 || $visit->getStatusCode() >= 300) {
                throw new \RuntimeException();
            }
        } catch (\Throwable) {
            $error = 'Návštěvu stránky se nepodařilo zaznamenat.';
        }

        return $this->render('website/public.html.twig', ['page' => $page, 'pageTitle' => $page['title'] ?? 'Web', 'error' => $error]);
    }

    private function overview(Request $request, string $module): Response
    {
        $overview = null;
        $companies = [];
        $error = '';
        try {
            $overview = $this->backend->json('GET', '/api/v1/'.$module.'/overview');
            if ($module === 'accounting') {
                $companies = $this->backend->json('GET', '/api/v1/companies');
            }
        } catch (\Throwable) {
            $error = 'Přehled modulu '.self::TITLES[$module].' nelze načíst. Backend není dostupný nebo požadavek odmítl.';
        }
        $editingPage = null;
        if ($module === 'website' && $request->query->has('edit')) {
            foreach ($overview['pages'] ?? [] as $page) {
                if (($page['id'] ?? '') === $request->query->get('edit')) {
                    $editingPage = $page;
                    break;
                }
            }
            if ($editingPage === null) {
                $error = trim($error.' Stránka pro úpravu nebyla nalezena.');
            }
        }

        return $this->render($module.'/index.html.twig', [
            'overview' => $overview, 'companies' => $companies, 'editingPage' => $editingPage,
            'error' => $error, 'actionError' => $request->query->get('error', ''),
            'message' => $request->query->get('message', ''), 'pageTitle' => self::TITLES[$module],
            'breadcrumb' => strtoupper($module),
        ]);
    }

    /** @param callable(): array{string, string, ?array, string} $operation */
    private function action(string $module, callable $operation): Response
    {
        $parameter = 'error';
        try {
            [$method, $path, $body, $success] = $operation();
            $response = $this->backend->request($method, $path, $body);
            $status = $response->getStatusCode();
            if ($status >= 200 && $status < 300) {
                $parameter = 'message';
                $message = $success;
            } else {
                $message = 'Backend odmítl změnu (HTTP '.$status.'). Změna nebyla uložena.';
                $details = $response->getContent(false);
                if ($details !== '') {
                    $message .= ' '.Utf8::truncate(strip_tags($details), 500);
                }
            }
        } catch (\InvalidArgumentException $exception) {
            $message = $exception->getMessage();
        } catch (\Throwable) {
            $message = 'Změnu se nepodařilo uložit. Backend není dostupný.';
        }
        $parameters = [$parameter => $message];
        if ($module === 'helpdesk') {
            $parameters['v'] = self::HELP_VERSION;
        }

        return $this->redirectToRoute('app_'.$module, $parameters);
    }

    private function invoicePdf(string $id): Response
    {
        if (!$this->validId($id)) {
            return new Response('Neplatné číslo faktury.', 400);
        }
        try {
            $response = $this->backend->request('GET', '/api/v1/accounting/invoices/'.$id.'/pdf');
            $status = $response->getStatusCode();
            if ($status < 200 || $status >= 300) {
                return new Response('PDF faktury není dostupné (HTTP '.$status.').', $status);
            }
            $headers = ['Content-Type' => 'application/pdf'];
            $disposition = $response->getHeaders(false)['content-disposition'][0] ?? null;
            if ($disposition !== null) {
                $headers['Content-Disposition'] = $disposition;
            }

            return new Response($response->getContent(false), $status, $headers);
        } catch (\Throwable) {
            return new Response('Stahování PDF se nezdařilo. Backend není dostupný.', 503);
        }
    }

    private function id(Request $request, bool $query = false): string
    {
        $id = ($query ? $request->query : $request->request)->get('id', '');
        if (!$this->validId($id)) {
            throw new \InvalidArgumentException('Neplatný identifikátor záznamu.');
        }

        return $id;
    }

    private function validId(string $id): bool
    {
        return preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/iD', $id) === 1;
    }

    private function text(Request $request, string $field, ?int $maximum = null): string
    {
        $value = trim((string) $request->request->get($field, ''));
        if ($value === '' || ($maximum !== null && Utf8::length($value) > $maximum)) {
            throw new \InvalidArgumentException('Vyplňte platnou hodnotu pole '.$field.'.');
        }

        return $value;
    }

    private function decimalText(Request $request, string $field): string
    {
        $value = $this->text($request, $field);
        if (!preg_match('/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/D', $value) || !is_finite((float) $value)) {
            throw new \InvalidArgumentException('Vyplňte platnou částku v poli '.$field.'.');
        }

        return $value;
    }

    private function decimal(Request $request, string $field, float $minimum): string
    {
        $value = $this->decimalText($request, $field);
        if ((float) $value < $minimum) {
            throw new \InvalidArgumentException('Částka v poli '.$field.' musí být alespoň '.$minimum.'.');
        }

        return $value;
    }

    private function integer(Request $request, string $field, int $minimum, int $maximum = PHP_INT_MAX): int
    {
        $value = filter_var($request->request->get($field), FILTER_VALIDATE_INT);
        if ($value === false || $value < $minimum || $value > $maximum) {
            throw new \InvalidArgumentException('Vyplňte platné celé číslo v poli '.$field.'.');
        }

        return $value;
    }

    private function date(Request $request, string $field): string
    {
        $value = $this->text($request, $field);
        $date = \DateTimeImmutable::createFromFormat('!Y-m-d', $value);
        if ($date === false || $date->format('Y-m-d') !== $value) {
            throw new \InvalidArgumentException('Vyplňte platné datum v poli '.$field.'.');
        }

        return $value;
    }
}
