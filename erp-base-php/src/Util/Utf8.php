<?php

namespace App\Util;

final class Utf8
{
    public static function length(string $value): int
    {
        $length = preg_match_all('/./us', $value);
        if ($length === false) {
            throw new \InvalidArgumentException('Text must be valid UTF-8.');
        }

        return $length;
    }

    public static function truncate(string $value, int $length): string
    {
        if ($length < 0) {
            throw new \InvalidArgumentException('Text length must not be negative.');
        }
        if (preg_match('/^.{0,'.$length.'}/us', $value, $match) !== 1) {
            throw new \InvalidArgumentException('Text must be valid UTF-8.');
        }

        return $match[0];
    }
}
