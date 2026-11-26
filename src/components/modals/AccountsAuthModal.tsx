import React, { useEffect, useRef, useState } from 'react';
import QRCode from 'qrcode';
import { Check, KeyRound, QrCode, Upload, UserCheck, X } from 'lucide-react';
import { LiquidGlassSurface } from '../liquid/LiquidGlassSurface';
import { parseCookieJson, type DesignStyle, type MeshThemePalette } from '../../lib/meshCore';
import type { LiquidOpticsConfig } from '../../lib/liquidGlassMath';
import type { Translations } from '../../lib/i18n';
import type { MeshAccount } from '../../data/meshSeedData';

interface AccountsAuthModalProps {
  open: boolean;
  onClose: () => void;
  accounts: MeshAccount[];
  activeAccountId: string;
  onSelectAccount: (id: string) => void;
  onAddLiveAccount: (account: MeshAccount) => void;
  designStyle: DesignStyle;
  optics: LiquidOpticsConfig;
  theme: MeshThemePalette;
  tr: Translations;
}

export const AccountsAuthModal: React.FC<AccountsAuthModalProps> = ({
  open,
  onClose,
  accounts,
  activeAccountId,
  onSelectAccount,
  onAddLiveAccount,
  designStyle,
  optics,
  theme,
  tr,
}) => {
  const [tab, setTab] = useState<'accounts' | 'qr' | 'cookie'>('accounts');
  const [cookieInput, setCookieInput] = useState('');
  const [statusError, setStatusError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const qrCanvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    if (!open || tab !== 'qr' || !qrCanvasRef.current) return;
    QRCode.toCanvas(
      qrCanvasRef.current,
      'https://school.mos.ru/v2/auth/qr?client=meshfoss-desktop&v=2.5',
      {
        width: 196,
        margin: 2,
        color: { dark: '#0B101B', light: '#FFFFFF' },
      },
      () => {}
    );
  }, [open, tab]);

  if (!open) return null;

  const textPrimary = theme.isLight ? 'text-slate-900' : 'text-slate-50';
  const textMuted = theme.isLight ? 'text-slate-600' : 'text-slate-300/80';
  const dividerClass = theme.isLight ? 'border-slate-900/10' : 'border-white/10';

  const handleFileImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      setCookieInput(String(reader.result ?? ''));
      setStatusError(null);
    };
    reader.readAsText(file);
  };

  const handleElectronPick = async () => {
    const bridge = (window as unknown as { meshfossDesktop?: { pickCookieFile: () => Promise<string | null> } })
      .meshfossDesktop;
    if (!bridge?.pickCookieFile) return;
    const content = await bridge.pickCookieFile();
    if (content) {
      setCookieInput(content);
      setStatusError(null);
    }
  };

  const handleSubmitCookieOrToken = async (e: React.FormEvent) => {
    e.preventDefault();
    setStatusError(null);
    setStatusMessage(null);

    const parsed = parseCookieJson(cookieInput);
    if (!parsed.ok) {
      setStatusError(parsed.error ?? 'Некорректный формат данных входа.');
      return;
    }

    setSubmitting(true);
    try {
      const bridge = (
        window as unknown as {
          meshfossDesktop?: {
            meshRequest: (payload: Record<string, unknown>) => Promise<{ ok: boolean; status: number; data?: unknown; error?: string }>;
          };
        }
      ).meshfossDesktop;

      const requestPayload = {
        endpoint: '/api/family/web/v1/profile',
        method: 'GET',
        token: parsed.token ?? undefined,
        cookie: parsed.cookieHeader || undefined,
      };

      const res = bridge?.meshRequest
        ? await bridge.meshRequest(requestPayload)
        : await fetch('/api/mesh/proxy', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestPayload),
          }).then((r) => r.json());

      if (res.ok && res.data && typeof res.data === 'object') {
        const profile = (res.data as { profile?: { first_name?: string; last_name?: string; id?: number } }).profile;
        const fullName = profile ? `${profile.first_name ?? ''} ${profile.last_name ?? ''}`.trim() : 'Профиль МЭШ';
        const newAcc: MeshAccount = {
          id: `live-${Date.now()}`,
          name: fullName || 'Пользователь МЭШ',
          initials: (fullName || 'МШ').slice(0, 2).toUpperCase(),
          role: 'student',
          school: 'school.mos.ru (Live)',
          className: 'Подключённый аккаунт',
          profileId: String(profile?.id ?? 'live'),
          personId: 'live-session',
          isLive: true,
          tokenPreview: parsed.token ? `${parsed.token.slice(0, 12)}…` : undefined,
        };
        onAddLiveAccount(newAcc);
        setStatusMessage('Сессия МЭШ подтверждена и сохранена.');
        setCookieInput('');
        setTab('accounts');
      } else {
        // Real server/token verification result — never silently fake success!
        setStatusError(
          res.status === 401 || res.status === 403
            ? 'school.mos.ru отклонил токен (401/403). Проверьте свежесть mos_cookies.json.'
            : `Не удалось подтвердить сессию в school.mos.ru (HTTP ${res.status || 'network'}). Токен распознан локально, проверьте подключение.`
        );
      }
    } catch {
      setStatusError('Нет ответа от прокси school.mos.ru. Проверьте соединение.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-md"
      role="dialog"
      aria-modal="true"
      aria-label={tr.accounts}
    >
      <LiquidGlassSurface
        cornerRadius={30}
        designStyle={designStyle}
        optics={optics}
        isLight={theme.isLight}
        className="w-full max-w-lg p-6"
      >
        <div className="flex items-center justify-between gap-4">
          <h2 className={`font-display text-lg font-bold ${textPrimary}`}>
            {tr.accounts} · МЭШ
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label={tr.close}
            className={`flex h-10 w-10 items-center justify-center rounded-full ${
              theme.isLight ? 'hover:bg-slate-900/10' : 'hover:bg-white/10'
            }`}
          >
            <X className={`h-5 w-5 ${textPrimary}`} />
          </button>
        </div>

        {/* Mode switch */}
        <div className="mt-4 grid grid-cols-3 gap-1.5">
          {(
            [
              ['accounts', tr.accounts, <UserCheck key="u" className="h-3.5 w-3.5" />],
              ['qr', 'QR-код', <QrCode key="q" className="h-3.5 w-3.5" />],
              ['cookie', 'Cookie / Токен', <KeyRound key="k" className="h-3.5 w-3.5" />],
            ] as const
          ).map(([key, label, icon]) => (
            <button
              key={key}
              type="button"
              onClick={() => {
                setTab(key);
                setStatusError(null);
              }}
              className={`flex min-h-[40px] items-center justify-center gap-1.5 rounded-2xl px-3 py-2 text-xs font-semibold transition-colors whitespace-nowrap ${
                tab === key
                  ? theme.isLight
                    ? 'bg-white text-slate-950 shadow-sm'
                    : 'bg-white/20 text-white'
                  : `${textMuted} hover:bg-white/10`
              }`}
            >
              {icon}
              <span>{label}</span>
            </button>
          ))}
        </div>

        {tab === 'accounts' && (
          <div className={`mt-5 divide-y ${dividerClass}`}>
            {accounts.map((acc) => {
              const active = acc.id === activeAccountId;
              return (
                <button
                  key={acc.id}
                  type="button"
                  onClick={() => {
                    onSelectAccount(acc.id);
                    onClose();
                  }}
                  className={`flex w-full items-center justify-between gap-3 py-3.5 text-left transition-colors ${
                    theme.isLight ? 'hover:bg-slate-900/5' : 'hover:bg-white/6'
                  }`}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div
                      className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl font-display text-sm font-bold text-white"
                      style={{ backgroundColor: theme.primary }}
                    >
                      {acc.initials}
                    </div>
                    <div className="min-w-0">
                      <p className={`truncate font-display text-sm font-semibold ${textPrimary}`}>
                        {acc.name}
                      </p>
                      <p className={`truncate text-xs ${textMuted}`}>
                        {acc.className} · {acc.school}
                      </p>
                    </div>
                  </div>
                  {active && <Check className="h-5 w-5 shrink-0 text-emerald-400" />}
                </button>
              );
            })}
          </div>
        )}

        {tab === 'qr' && (
          <div className="mt-5 flex flex-col items-center gap-4 text-center">
            <div className="rounded-3xl bg-white p-3.5 shadow-lg">
              <canvas ref={qrCanvasRef} />
            </div>
            <p className={`max-w-sm text-xs leading-relaxed ${textMuted}`}>
              Отсканируйте QR-код приложением «Госуслуги Москвы» или камерой телефона для входа в{' '}
              <span className={textPrimary}>school.mos.ru</span>.
            </p>
          </div>
        )}

        {tab === 'cookie' && (
          <form onSubmit={handleSubmitCookieOrToken} className="mt-5 space-y-4">
            <div className="flex items-center justify-between gap-2">
              <label htmlFor="cookie-json-input" className={`text-xs font-medium ${textPrimary}`}>
                mos_cookies.json или JWT-токен МЭШ
              </label>
              <label className="inline-flex cursor-pointer items-center gap-1.5 text-xs font-semibold text-sky-400 hover:underline">
                <Upload className="h-3.5 w-3.5" aria-hidden="true" />
                <span>Загрузить файл</span>
                <input
                  type="file"
                  accept=".json,.txt"
                  onChange={handleFileImport}
                  onClick={() => void handleElectronPick()}
                  className="sr-only"
                />
              </label>
            </div>

            <textarea
              id="cookie-json-input"
              rows={4}
              value={cookieInput}
              onChange={(e) => {
                setCookieInput(e.target.value);
                setStatusError(null);
              }}
              placeholder='[{"name":"aupd_token","value":"eyJ..."}]'
              className={`w-full rounded-2xl border p-3 font-mono text-xs outline-none focus:border-sky-400 ${
                theme.isLight
                  ? 'border-slate-900/15 bg-white/80 text-slate-900'
                  : 'border-white/15 bg-white/6 text-white'
              }`}
            />

            {statusError && (
              <p role="alert" className="text-xs font-medium text-rose-400">
                {statusError}
              </p>
            )}
            {statusMessage && (
              <p className="text-xs font-medium text-emerald-400">{statusMessage}</p>
            )}

            <div className="flex justify-end gap-2">
              <button
                type="submit"
                disabled={submitting}
                className="min-h-[42px] rounded-2xl px-5 py-2 text-xs font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-50"
                style={{ backgroundColor: theme.primary }}
              >
                {submitting ? 'Проверка в school.mos.ru…' : tr.signInMesh}
              </button>
            </div>
          </form>
        )}
      </LiquidGlassSurface>
    </div>
  );
};
