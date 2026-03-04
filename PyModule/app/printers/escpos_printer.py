import logging
from typing import Iterable

from app.config import settings

logger = logging.getLogger(__name__)


def _parse_usb_id(value: str | None) -> int | None:
    if value is None:
        return None
    v = value.strip().lower()
    if not v:
        return None
    try:
        if v.startswith("0x"):
            return int(v, 16)
        return int(v)
    except Exception:
        return None


class EscposPrinter:
    def print_lines(self, lines: Iterable[str]) -> None:
        mode = (settings.ESCPOS_CONNECTION or "usb").strip().lower()

        if mode == "windows":
            ok, msg = self._print_windows_raw(lines)
            if ok:
                return
            logger.warning("Windows raw print failed: %s", msg)
            self._print_stdout(lines)
            return

        ok, msg = self._print_usb(lines)
        if ok:
            return

        logger.warning("USB ESC/POS print failed: %s", msg)
        ok_win, msg_win = self._print_windows_raw(lines)
        if ok_win:
            return

        logger.warning("Windows raw fallback failed: %s", msg_win)
        self._print_stdout(lines)

    def _print_usb(self, lines: Iterable[str]) -> tuple[bool, str]:
        vendor_id = _parse_usb_id(settings.ESCPOS_USB_VENDOR_ID)
        product_id = _parse_usb_id(settings.ESCPOS_USB_PRODUCT_ID)
        if vendor_id is None or product_id is None:
            return False, "ESCPOS_USB_VENDOR_ID / ESCPOS_USB_PRODUCT_ID не заданы"

        try:
            from escpos.printer import Usb  # type: ignore
        except Exception as exc:
            return False, f"python-escpos недоступен: {exc}"

        try:
            printer = Usb(
                vendor_id,
                product_id,
                timeout=int(settings.ESCPOS_USB_TIMEOUT_MS or 3000),
            )
            # 1) Крупно номер/заголовок
            printer.set(align="center", width=2, height=2, bold=True)
            lines_list = list(lines)
            if lines_list:
                printer.text(f"{lines_list[0]}\n")
            printer.set(align="left", width=1, height=1, bold=False)
            for line in lines_list[1:]:
                printer.text(f"{line}\n")
            printer.text("\n")
            printer.cut()
            printer.close()
            return True, "printed via usb"
        except Exception as exc:
            return False, str(exc)

    def _print_windows_raw(self, lines: Iterable[str]) -> tuple[bool, str]:
        printer_name = (settings.PRINTER_NAME or "").strip()
        if not printer_name:
            return False, "PRINTER_NAME не задан"
        try:
            import win32print  # type: ignore
        except Exception as exc:
            return False, f"pywin32 недоступен: {exc}"

        try:
            handle = win32print.OpenPrinter(printer_name)
            try:
                job = win32print.StartDocPrinter(handle, 1, ("Cafehelp Receipt", None, "RAW"))
                try:
                    win32print.StartPagePrinter(handle)
                    payload = ""
                    lines_list = list(lines)
                    if lines_list:
                        payload += f"\x1b\x61\x01\x1d\x21\x11{lines_list[0]}\n\x1d\x21\x00\x1b\x61\x00"
                        for line in lines_list[1:]:
                            payload += f"{line}\n"
                    payload += "\n\n\x1d\x56\x00"
                    win32print.WritePrinter(handle, payload.encode("cp866", errors="replace"))
                    win32print.EndPagePrinter(handle)
                finally:
                    win32print.EndDocPrinter(handle)
            finally:
                win32print.ClosePrinter(handle)
            return True, "printed via windows raw"
        except Exception as exc:
            return False, str(exc)

    def _print_stdout(self, lines: Iterable[str]) -> None:
        print("=== ESC/POS PRINT (STDOUT FALLBACK) ===")
        for line in lines:
            print(line)
        print("=== END ===")
