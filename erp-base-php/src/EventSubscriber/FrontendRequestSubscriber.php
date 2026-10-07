<?php

namespace App\EventSubscriber;

use App\Service\BackendApiClient;
use App\Service\BackendApiException;
use Psr\Log\LoggerInterface;
use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpKernel\Event\ResponseEvent;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\HttpKernel\KernelEvents;
use Symfony\Contracts\HttpClient\Exception\TransportExceptionInterface;

final class FrontendRequestSubscriber implements EventSubscriberInterface
{
    private const PRIVATE_PATHS = [
        '/', '/apps', '/dashboard', '/roles', '/role-modules', '/companies', '/settings', '/users',
        '/accounting', '/crm', '/documents', '/ecommerce', '/helpdesk', '/hr', '/inventory',
        '/manufacturing', '/marketing', '/planning', '/pos', '/projects', '/promo', '/purchase',
        '/sales', '/website',
    ];

    /** @var array<string, array{path: string, name: string}> */
    private const MODULE_CHECKS = [
        '/companies' => ['path' => '/api/v1/companies', 'name' => 'Společnosti'],
        '/accounting' => ['path' => '/api/v1/accounting/overview', 'name' => 'Účetnictví'],
        '/crm' => ['path' => '/api/v1/crm/overview', 'name' => 'CRM'],
        '/documents' => ['path' => '/api/v1/documents/overview', 'name' => 'Dokumenty'],
        '/ecommerce' => ['path' => '/api/v1/catalog/homepage', 'name' => 'eCommerce'],
        '/helpdesk' => ['path' => '/api/v1/helpdesk/overview', 'name' => 'Helpdesk'],
        '/hr' => ['path' => '/api/v1/hr/overview', 'name' => 'Lidé'],
        '/inventory' => ['path' => '/api/v1/inventory/overview', 'name' => 'Sklad'],
        '/manufacturing' => ['path' => '/api/v1/manufacturing/overview', 'name' => 'Výroba'],
        '/marketing' => ['path' => '/api/v1/marketing/overview', 'name' => 'Marketing'],
        '/planning' => ['path' => '/api/v1/planning/overview', 'name' => 'Plánování'],
        '/pos' => ['path' => '/api/v1/pos/overview', 'name' => 'Pokladna'],
        '/projects' => ['path' => '/api/v1/projects/overview', 'name' => 'Projekty'],
        '/promo' => ['path' => '/api/v1/promo-campaigns', 'name' => 'Promo kampaně'],
        '/purchase' => ['path' => '/api/v1/purchase/overview', 'name' => 'Nákup'],
        '/sales' => ['path' => '/api/v1/sales/overview', 'name' => 'Prodej'],
        '/website' => ['path' => '/api/v1/website/overview', 'name' => 'Web'],
        '/roles' => ['path' => '/api/v1/roles', 'name' => 'Role a oprávnění'],
        '/role-modules' => ['path' => '/api/v1/roles', 'name' => 'Role pro moduly'],
        '/settings' => ['path' => '/api/v1/settings', 'name' => 'Nastavení'],
        '/users' => ['path' => '/api/v1/users', 'name' => 'Uživatelé'],
    ];

    public function __construct(
        private readonly BackendApiClient $backend,
        private readonly LoggerInterface $logger,
    ) {
    }

    public static function getSubscribedEvents(): array
    {
        return [
            KernelEvents::REQUEST => ['onRequest', 8],
            KernelEvents::RESPONSE => ['onResponse', -8],
        ];
    }

    public function onRequest(RequestEvent $event): void
    {
        if (!$event->isMainRequest()) {
            return;
        }

        $request = $event->getRequest();
        $path = $request->getPathInfo();
        $session = $request->getSession();
        $expiresAt = $session->get('expiresAt');
        if (is_int($expiresAt) && $expiresAt < time()) {
            $session->invalidate();
        }

        if (!in_array($request->getMethod(), ['GET', 'HEAD', 'OPTIONS'], true) && !$this->isSameOrigin($request)) {
            $event->setResponse(new \Symfony\Component\HttpFoundation\Response(
                'Request origin could not be verified.',
                403,
                ['X-Content-Type-Options' => 'nosniff', 'Referrer-Policy' => 'same-origin'],
            ));

            return;
        }

        if (!in_array($path, self::PRIVATE_PATHS, true)) {
            return;
        }

        $token = $session->get('backendToken');
        if (!is_string($token) || $token === '') {
            $event->setResponse(new RedirectResponse('/login'));

            return;
        }

        $request->attributes->set('frontendUser', [
            'name' => $session->get('userName', 'Uživatel'),
            'role' => $session->get('roleName', 'Bez role'),
        ]);

        if ($request->isMethod('GET') && isset(self::MODULE_CHECKS[$path])) {
            $module = self::MODULE_CHECKS[$path];
            try {
                $response = $this->backend->request('GET', $module['path']);
                if ($response->getStatusCode() === 403) {
                    $event->setResponse(new RedirectResponse('/apps?error='.rawurlencode(
                        'Uživatel nemá oprávnění k modulu '.$module['name'].'.',
                    )));
                }
            } catch (BackendApiException $exception) {
                if ($exception->statusCode === 403) {
                    $event->setResponse(new RedirectResponse('/apps?error='.rawurlencode(
                        'Uživatel nemá oprávnění k modulu '.$module['name'].'.',
                    )));
                } else {
                    $this->logger->warning('Module permission check failed.', ['exception' => $exception]);
                }
            } catch (TransportExceptionInterface $exception) {
                $this->logger->error('Module permission check could not reach the backend.', ['exception' => $exception]);
            }
        }
    }

    public function onResponse(ResponseEvent $event): void
    {
        if (!$event->isMainRequest() || str_starts_with($event->getRequest()->getPathInfo(), '/assets/')) {
            return;
        }

        $response = $event->getResponse();
        $response->headers->set('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
        $response->headers->set('Pragma', 'no-cache');
        $response->headers->set('Expires', '0');
        $response->headers->set('X-Content-Type-Options', 'nosniff');
        $response->headers->set('Referrer-Policy', 'same-origin');
    }

    private function isSameOrigin(Request $request): bool
    {
        $origin = $request->headers->get('Origin') ?? $request->headers->get('Referer');
        if ($origin === null) {
            return false;
        }

        $originParts = parse_url($origin);
        if (!is_array($originParts) || !isset($originParts['scheme'], $originParts['host'])) {
            return false;
        }

        $originPort = $originParts['port'] ?? ($originParts['scheme'] === 'https' ? 443 : 80);

        return strtolower($originParts['scheme']) === $request->getScheme()
            && strcasecmp($originParts['host'], $request->getHost()) === 0
            && $originPort === $request->getPort();
    }
}
