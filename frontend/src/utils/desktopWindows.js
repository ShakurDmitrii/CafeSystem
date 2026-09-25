import { isTauri } from "@tauri-apps/api/core";

const PRINT_JOB_PREFIX = "cafehelp_print_job_";

function windowFeatures({ width = 1100, height = 760 } = {}) {
    return `width=${width},height=${height},resizable=yes,scrollbars=yes`;
}

export async function openAppWindow({ label, path, title, width = 1100, height = 760 }) {
    const url = `${window.location.origin}${path}`;
    if (!isTauri()) {
        const popup = window.open(url, "_blank", windowFeatures({ width, height }));
        if (!popup) throw new Error("Не удалось открыть новое окно");
        popup.focus?.();
        return popup;
    }

    const { WebviewWindow } = await import("@tauri-apps/api/webviewWindow");
    const existing = await WebviewWindow.getByLabel(label);
    if (existing) {
        await existing.setFocus();
        return existing;
    }

    const nativeWindow = new WebviewWindow(label, {
        url,
        title,
        width,
        height,
        minWidth: Math.min(width, 520),
        minHeight: Math.min(height, 420),
        resizable: true,
        center: true,
        focus: true
    });
    await new Promise((resolve, reject) => {
        nativeWindow.once("tauri://created", resolve);
        nativeWindow.once("tauri://error", (event) => reject(new Error(String(event.payload))));
    });
    return nativeWindow;
}

export async function openPrintDocument({ html, title, width = 500, height = 800 }) {
    if (!isTauri()) {
        const popup = window.open("", "_blank", windowFeatures({ width, height }));
        if (!popup) throw new Error("Не удалось открыть окно печати");
        popup.document.write(html);
        popup.document.close();
        popup.focus();
        popup.print();
        return popup;
    }

    const jobId = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    localStorage.setItem(`${PRINT_JOB_PREFIX}${jobId}`, html);
    try {
        return await openAppWindow({
            label: `print-${jobId}`,
            path: `/desktop-print/${jobId}`,
            title,
            width,
            height
        });
    } catch (error) {
        localStorage.removeItem(`${PRINT_JOB_PREFIX}${jobId}`);
        throw error;
    }
}

export function takePrintJob(jobId) {
    const key = `${PRINT_JOB_PREFIX}${jobId}`;
    const html = localStorage.getItem(key);
    localStorage.removeItem(key);
    return html;
}
