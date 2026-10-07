<?php

namespace App\Service;

final class BackendApiException extends \RuntimeException
{
    public function __construct(
        public readonly int $statusCode,
        public readonly string $path,
    ) {
        parent::__construct(sprintf('Backend returned HTTP %d for %s.', $statusCode, $path));
    }
}
