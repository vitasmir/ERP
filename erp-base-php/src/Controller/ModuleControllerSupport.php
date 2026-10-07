<?php

namespace App\Controller;

use App\Service\BackendApiClient;
use App\Service\BackendApiException;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Contracts\HttpClient\ResponseInterface;

abstract class ModuleControllerSupport extends AbstractController
{
    protected function uuid(mixed $value): string
    {
        if (!is_string($value) || !preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/iD', $value)) {
            throw new \InvalidArgumentException('Neplatný identifikátor.');
        }

        return strtolower($value);
    }

    protected function optionalUuid(mixed $value): ?string
    {
        return $value === null || (is_string($value) && trim($value) === '') ? null : $this->uuid($value);
    }

    protected function integer(mixed $value, int $minimum = PHP_INT_MIN): int
    {
        if ((!is_string($value) && !is_int($value)) || !preg_match('/^[+-]?\d+$/D', (string) $value)) {
            throw new \InvalidArgumentException('Neplatné množství.');
        }
        $digits = ltrim(ltrim((string) $value, '+-'), '0');
        $normalized = (str_starts_with((string) $value, '-') && $digits !== '' ? '-' : '').($digits === '' ? '0' : $digits);
        $number = filter_var($normalized, FILTER_VALIDATE_INT);
        if ($number === false || $number < $minimum) {
            throw new \InvalidArgumentException('Neplatné množství.');
        }

        return $number;
    }

    protected function decimal(mixed $value, bool $optional = false): ?string
    {
        if ($optional && ($value === null || (is_string($value) && trim($value) === ''))) {
            return null;
        }
        if ((!is_string($value) && !is_int($value) && !is_float($value)) || !preg_match('/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/D', (string) $value) || !is_finite((float) $value)) {
            throw new \InvalidArgumentException('Neplatná cena.');
        }

        // Keep decimal precision intact; the backend accepts BigDecimal JSON strings.
        return (string) $value;
    }

    protected function date(?string $value): string
    {
        $date = \DateTimeImmutable::createFromFormat('!Y-m-d', $value ?? '');
        if (!$date || $date->format('Y-m-d') !== $value) {
            throw new \InvalidArgumentException('Neplatné datum.');
        }

        return $value;
    }

    /** @param list<string> $fields */
    protected function fields(Request $request, array $fields): array
    {
        $values = [];
        foreach ($fields as $field) {
            $values[$field] = $request->request->get($field);
        }

        return $values;
    }

    protected function mutate(BackendApiClient $backend, string $method, string $path, ?array $body = null, bool $authenticated = true): ResponseInterface
    {
        $response = $backend->request($method, $path, $body, $authenticated);
        $status = $response->getStatusCode();
        if ($status < 200 || $status >= 300) {
            throw new BackendApiException($status, $path);
        }

        return $response;
    }

    protected function feedback(string $path, string $text, bool $error = false, array $parameters = [], string $fragment = ''): Response
    {
        return $this->redirect($path.'?'.http_build_query([$error ? 'error' : 'message' => $text] + $parameters).$fragment);
    }

    protected function normalizedRole(Request $request): string
    {
        $role = (string) $request->getSession()->get('roleName', '');
        if (class_exists(\Normalizer::class)) {
            $role = preg_replace('/\p{M}/u', '', \Normalizer::normalize($role, \Normalizer::FORM_D)) ?? $role;
        } else {
            $role = strtr($role, [
                'á' => 'a', 'č' => 'c', 'ď' => 'd', 'é' => 'e', 'ě' => 'e', 'í' => 'i', 'ň' => 'n', 'ó' => 'o',
                'ř' => 'r', 'š' => 's', 'ť' => 't', 'ú' => 'u', 'ů' => 'u', 'ý' => 'y', 'ž' => 'z',
                'Á' => 'A', 'Č' => 'C', 'Ď' => 'D', 'É' => 'E', 'Ě' => 'E', 'Í' => 'I', 'Ň' => 'N', 'Ó' => 'O',
                'Ř' => 'R', 'Š' => 'S', 'Ť' => 'T', 'Ú' => 'U', 'Ů' => 'U', 'Ý' => 'Y', 'Ž' => 'Z',
            ]);
        }

        return strtolower(trim($role));
    }
}
