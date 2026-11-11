import styles from "./VoiceOrderPanel.module.css";
import { formatMoney } from "../cashier-page/cashierUtils";
import { describeAdded } from "./voiceDraft";
import { VOICE_HOTKEY_OPTIONS, hotkeyLabel } from "./voiceOrderSettings";

/**
 * Панель голосового набора внутри окна выбора блюд.
 * Показывает состояние записи, распознанную фразу и всё, что требует решения кассира.
 * Позиции попадают в корзину окна; в заказ они уйдут только после «ОК».
 */
export default function VoiceOrderPanel({
    supported,
    phase,
    hotkey,
    error,
    result,
    disabled,
    onToggle,
    onCancel,
    onPickCandidate,
    onDismiss,
    onHotkeyChange
}) {
    if (!supported) {
        return (
            <div className={`${styles.panel} ${styles.panelMuted}`} role="status">
                Голосовой набор недоступен: в этом окружении нет доступа к микрофону.
            </div>
        );
    }

    const isRecording = phase === "recording";
    const isProcessing = phase === "processing";
    const label = hotkeyLabel(hotkey);

    return (
        <div className={styles.panel} aria-live="polite">
            <div className={styles.controls}>
                <button
                    type="button"
                    className={`${styles.micButton} ${isRecording ? styles.micButtonRecording : ""}`}
                    onClick={onToggle}
                    disabled={disabled || isProcessing}
                    aria-pressed={isRecording}
                >
                    <span className={styles.micDot} aria-hidden="true" />
                    {isRecording ? "Говорите… нажмите ещё раз, чтобы закончить" : isProcessing ? "Распознаём…" : `Голосом (${label})`}
                </button>
                {isRecording && (
                    <button type="button" className={styles.linkButton} onClick={onCancel}>
                        Отмена (Esc)
                    </button>
                )}
                <label className={styles.hotkeyField}>
                    <span>Клавиша</span>
                    <select value={hotkey} onChange={(event) => onHotkeyChange(event.target.value)} disabled={isRecording}>
                        {VOICE_HOTKEY_OPTIONS.map((option) => (
                            <option key={option.key} value={option.key}>{option.label}</option>
                        ))}
                    </select>
                </label>
                <span className={styles.hint}>
                    Формат: «блюдо, количество, … три персоны». Число относится к блюду перед ним.
                </span>
            </div>

            {error && (
                <div className={styles.error} role="alert">{error}</div>
            )}

            {result && (
                <div className={styles.result}>
                    <div className={styles.resultHeader}>
                        <span>
                            Распознано: <q>{result.transcript || "—"}</q>
                        </span>
                        <button type="button" className={styles.linkButton} onClick={onDismiss}>Скрыть</button>
                    </div>

                    {result.added.length > 0 && (
                        <div className={styles.added}>В корзину: {describeAdded(result.added)}</div>
                    )}
                    {result.personCount && (
                        <div className={styles.added}>Персон: {result.personCount} (применится после «ОК»)</div>
                    )}

                    {result.ambiguous.map((entry) => (
                        <div key={`${entry.text}-${entry.qty}`} className={styles.ambiguous}>
                            <div className={styles.ambiguousTitle}>
                                «{entry.text}» ×{entry.qty} — уточните позицию:
                            </div>
                            <div className={styles.candidates}>
                                {entry.candidates.map((candidate) => (
                                    <button
                                        key={`${candidate.itemType}-${candidate.id}`}
                                        type="button"
                                        className={styles.candidate}
                                        onClick={() => onPickCandidate(entry, candidate)}
                                        disabled={disabled}
                                    >
                                        <strong>{candidate.name}</strong>
                                        <small>{candidate.itemType === "set" ? "Набор" : "Блюдо"} · {formatMoney(candidate.price || 0)}</small>
                                    </button>
                                ))}
                            </div>
                        </div>
                    ))}

                    {result.unrecognized.length > 0 && (
                        <div className={styles.unrecognized}>
                            Не нашли в меню: {result.unrecognized.join(", ")}
                        </div>
                    )}

                    {result.added.length === 0 && result.ambiguous.length === 0 && result.unrecognized.length === 0 && (
                        <div className={styles.unrecognized}>Ничего не услышали. Попробуйте ещё раз ближе к микрофону.</div>
                    )}
                </div>
            )}
        </div>
    );
}
