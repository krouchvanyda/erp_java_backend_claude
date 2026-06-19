<?php

namespace App\Features\Devices\Services;

use Illuminate\Support\Facades\Log;
use Kreait\Firebase\Factory;
use Kreait\Firebase\Messaging\AndroidConfig;
use Kreait\Firebase\Messaging\ApnsConfig;
use Kreait\Firebase\Messaging\CloudMessage;
use Throwable;

/**
 * Thin wrapper over the Firebase Admin SDK (kreait). Pushes are data-only (no
 * notification block) so the Flutter background handler runs and the call sheet
 * can be drawn with Accept/Reject buttons. All sends are exception-swallowing —
 * a failed FCM delivery must never block the underlying call/REST flow.
 *
 * Port of the Spring FcmService. Stays a no-op unless FCM_ENABLED=true and a
 * service-account JSON path is configured.
 */
class FcmService
{
    /** @var \Kreait\Firebase\Contract\Messaging|null */
    private $messaging = null;

    /** @var bool */
    private $ready = false;

    /** @var bool */
    private $initialised = false;

    private function init(): void
    {
        if ($this->initialised) {
            return;
        }
        $this->initialised = true;

        if (! (bool) config('erp.fcm.enabled')) {
            Log::warning('[fcm] disabled (FCM_ENABLED=false). Call invites will not push.');
            return;
        }
        $saPath = (string) config('erp.fcm.service_account_json_path');
        if ($saPath === '' || ! is_file($saPath)) {
            Log::warning('[fcm] FCM_ENABLED=true but service-account JSON is missing — disabling.');
            return;
        }

        try {
            $this->messaging = (new Factory())->withServiceAccount($saPath)->createMessaging();
            $this->ready = true;
            Log::info('[fcm] initialised from '.$saPath);
        } catch (Throwable $e) {
            Log::error('[fcm] failed to initialise from '.$saPath.': '.$e->getMessage());
        }
    }

    public function isReady(): bool
    {
        $this->init();
        return $this->ready;
    }

    /**
     * Fire-and-forget data-only push to every supplied token. Failures are
     * logged at WARN and never propagated.
     *
     * @param array<int, string> $tokens
     * @param array<string, string|null> $data
     */
    public function sendDataToTokens(array $tokens, array $data): void
    {
        $this->init();
        if (! $this->ready) {
            Log::debug('[fcm] skip send — service not ready (tokens='.count($tokens).')');
            return;
        }
        if (empty($tokens)) {
            return;
        }

        $safeData = [];
        foreach ($data as $k => $v) {
            $safeData[$k] = $v === null ? '' : (string) $v;
        }

        $android = AndroidConfig::fromArray(['priority' => 'high']);
        $apns = ApnsConfig::fromArray([
            'headers' => ['apns-priority' => '10', 'apns-push-type' => 'alert'],
            'payload' => ['aps' => ['content-available' => 1]],
        ]);

        foreach ($tokens as $token) {
            try {
                $message = CloudMessage::new()
                    ->withData($safeData)
                    ->withAndroidConfig($android)
                    ->withApnsConfig($apns)
                    ->toToken($token);
                $id = $this->messaging->send($message);
                Log::info('[fcm] sent type='.($safeData['type'] ?? '').' to token=…'.$this->tail($token).' id='.$id);
            } catch (Throwable $e) {
                Log::warning('[fcm] send failed type='.($safeData['type'] ?? '').' token=…'.$this->tail($token).': '.$e->getMessage());
            }
        }
    }

    private function tail(string $t): string
    {
        return strlen($t) < 6 ? '?' : substr($t, -6);
    }
}
