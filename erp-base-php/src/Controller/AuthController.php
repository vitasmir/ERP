<?php

namespace App\Controller;

use App\Service\BackendApiClient;
use App\Service\BackendApiException;
use Psr\Log\LoggerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Contracts\HttpClient\Exception\TransportExceptionInterface;

final class AuthController extends AbstractController
{
    #[Route('/login', name: 'app_login', methods: ['GET', 'POST'])]
    public function login(Request $request, BackendApiClient $backend, LoggerInterface $logger): Response
    {
        if ($request->isMethod('GET')) {
            return $this->render('auth/login.html.twig', [
                'error' => $request->query->get('error'),
                'authenticated' => $request->getSession()->has('backendToken'),
            ], new Response(headers: ['Cache-Control' => 'no-store']));
        }

        $username = $request->request->getString('username');
        $password = $request->request->getString('password');
        if ($username === '' || $password === '') {
            return $this->render('auth/login.html.twig', [
                'error' => 'Zadejte uživatelské jméno a heslo.',
                'authenticated' => false,
            ], new Response(status: Response::HTTP_BAD_REQUEST, headers: ['Cache-Control' => 'no-store']));
        }

        try {
            $user = $backend->json('POST', '/api/v1/auth/login', [
                'username' => $username,
                'password' => $password,
            ], false);
        } catch (BackendApiException $exception) {
            if ($exception->statusCode >= 500) {
                $logger->error('ERP backend rejected or failed login.', ['exception' => $exception]);
                $error = 'Přihlašovací služba není dostupná.';
            } else {
                $error = 'Přihlášení se nezdařilo.';
            }

            return $this->render('auth/login.html.twig', [
                'error' => $error,
                'authenticated' => false,
            ], new Response(status: Response::HTTP_UNAUTHORIZED, headers: ['Cache-Control' => 'no-store']));
        } catch (TransportExceptionInterface $exception) {
            $logger->error('ERP backend is unavailable during login.', ['exception' => $exception]);

            return $this->render('auth/login.html.twig', [
                'error' => 'Přihlašovací služba není dostupná.',
                'authenticated' => false,
            ], new Response(status: Response::HTTP_SERVICE_UNAVAILABLE, headers: ['Cache-Control' => 'no-store']));
        }

        $token = $user['token'] ?? null;
        if (!is_string($token) || $token === '') {
            throw new \UnexpectedValueException('ERP login response did not include a token.');
        }

        $session = $request->getSession();
        $session->migrate(true);
        $session->set('backendToken', $token);
        $session->set('userName', (string) ($user['fullName'] ?? 'Uživatel'));
        $session->set('roleName', (string) ($user['roleName'] ?? 'Bez role'));
        $session->set('expiresAt', time() + 8 * 60 * 60);
        $session->setMaxInactiveInterval(1800);

        return $this->redirectToRoute('app_home');
    }

    #[Route('/logout', name: 'app_logout', methods: ['POST'])]
    public function logout(Request $request, BackendApiClient $backend, LoggerInterface $logger): Response
    {
        try {
            $response = $backend->request('POST', '/api/v1/auth/logout');
            if ($response->getStatusCode() >= 500) {
                $logger->warning('ERP backend returned an error while logging out.', [
                    'status' => $response->getStatusCode(),
                ]);
            }
        } catch (TransportExceptionInterface $exception) {
            $logger->error('ERP backend is unavailable during logout.', ['exception' => $exception]);
        }

        $request->getSession()->invalidate();

        return $this->redirectToRoute('app_login');
    }
}
