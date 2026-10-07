<?php

namespace App\Service;

use Symfony\Component\HttpFoundation\RequestStack;
use Symfony\Contracts\HttpClient\HttpClientInterface;
use Symfony\Contracts\HttpClient\ResponseInterface;

final class BackendApiClient
{
    public function __construct(
        private readonly HttpClientInterface $client,
        private readonly RequestStack $requestStack,
        private readonly string $backendUrl,
    ) {
    }

    /**
     * @param array<string, mixed>|null $json
     */
    public function request(string $method, string $path, ?array $json = null, bool $authenticated = true): ResponseInterface
    {
        if (!str_starts_with($path, '/api/v1/')) {
            throw new \InvalidArgumentException('Backend requests must use an /api/v1/ path.');
        }

        $options = [
            'timeout' => 5,
            'max_duration' => 15,
        ];
        if ($json !== null) {
            $options['json'] = $json;
        }

        $token = $this->requestStack->getCurrentRequest()?->getSession()->get('backendToken');
        if ($authenticated && is_string($token) && $token !== '') {
            $options['auth_bearer'] = $token;
        }

        return $this->client->request($method, rtrim($this->backendUrl, '/').$path, $options);
    }

    /**
     * @return array<string, mixed>|list<mixed>
     */
    public function json(string $method, string $path, ?array $json = null, bool $authenticated = true): array
    {
        $response = $this->request($method, $path, $json, $authenticated);
        $status = $response->getStatusCode();
        if ($status < 200 || $status >= 300) {
            throw new BackendApiException($status, $path);
        }

        $data = $response->toArray(false);
        if (!is_array($data)) {
            throw new \UnexpectedValueException(sprintf('Backend response for %s was not a JSON object or array.', $path));
        }

        return $data;
    }
}
