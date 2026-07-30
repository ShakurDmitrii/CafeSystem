import json
import os
import random
import time
from typing import Any

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry


VK_API_VERSION = "5.199"
GROUP_TOKEN = os.getenv("VK_GROUP_TOKEN", "").strip()
GROUP_ID = os.getenv("VK_GROUP_ID", "").strip()
BACKEND_URL = os.getenv("BACKEND_URL", "http://backend:8080").rstrip("/")
BOT_API_TOKEN = os.getenv("VK_BOT_API_TOKEN", "").strip()
POLL_WAIT = int(os.getenv("VK_LONG_POLL_WAIT", "10"))

HTTP = requests.Session()
HTTP.mount(
    "https://",
    HTTPAdapter(
        max_retries=Retry(
            total=3,
            connect=3,
            read=3,
            backoff_factor=1,
            status_forcelist=(429, 500, 502, 503, 504),
            allowed_methods=None,
        )
    ),
)


def keyboard_button(label: str, color: str = "secondary") -> dict[str, Any]:
    return {
        "action": {
            "type": "text",
            "label": label,
        },
        "color": color,
    }


def keyboard(rows: list[list[dict[str, Any]]]) -> str:
    return json.dumps(
        {
            "one_time": False,
            "inline": False,
            "buttons": rows,
        },
        ensure_ascii=False,
    )


LINK_KEYBOARD = keyboard([
    [keyboard_button("Ввести код", "primary")],
    [keyboard_button("Меню")],
])

MAIN_KEYBOARD = keyboard([
    [keyboard_button("Последний заказ", "primary"), keyboard_button("История заказов", "secondary")],
    [keyboard_button("Долги", "negative"), keyboard_button("Отвязать", "secondary")],
])


def backend_headers() -> dict[str, str]:
    headers = {"Content-Type": "application/json"}
    if BOT_API_TOKEN:
        headers["X-VK-Bot-Token"] = BOT_API_TOKEN
    return headers


def backend_get(path: str, **params: Any) -> requests.Response:
    return HTTP.get(
        f"{BACKEND_URL}{path}",
        params=params,
        headers=backend_headers(),
        timeout=15,
    )


def backend_post(path: str, json: dict[str, Any] | None = None, **params: Any) -> requests.Response:
    return HTTP.post(
        f"{BACKEND_URL}{path}",
        params=params,
        json=json,
        headers=backend_headers(),
        timeout=15,
    )


def vk_api(method: str, **params: Any) -> dict[str, Any]:
    payload = {
        "access_token": GROUP_TOKEN,
        "v": VK_API_VERSION,
        **params,
    }
    response = HTTP.post(f"https://api.vk.com/method/{method}", data=payload, timeout=20)
    data = response.json()
    if "error" in data:
        raise RuntimeError(data["error"])
    return data["response"]


def send_message(peer_id: int, text: str, keyboard_json: str | None = None) -> None:
    params = {
        "peer_id": peer_id,
        "random_id": random.randint(1, 2_000_000_000),
        "message": text,
    }
    if keyboard_json:
        params["keyboard"] = keyboard_json
    vk_api(
        "messages.send",
        **params,
    )


def get_long_poll_server() -> dict[str, Any]:
    if not GROUP_TOKEN or not GROUP_ID:
        raise RuntimeError("VK_GROUP_TOKEN and VK_GROUP_ID must be set")
    return vk_api("groups.getLongPollServer", group_id=GROUP_ID)


def is_long_poll_disabled_error(exc: Exception) -> bool:
    text = str(exc)
    return "longpoll for this group is not enabled" in text or "'error_code': 100" in text


def normalize_code(text: str) -> str | None:
    digits = "".join(ch for ch in text if ch.isdigit())
    return digits if len(digits) == 6 else None


def format_order(order: dict[str, Any]) -> str:
    items = order.get("items") or []
    lines = [
        f"Заказ #{order.get('orderId')}",
        f"Дата: {order.get('created_at') or order.get('date') or 'не указана'}",
        f"Сумма: {float(order.get('amount') or 0):.2f} руб.",
        f"Статус: {'готов' if order.get('status') else 'готовится'}",
    ]
    if order.get("duty"):
        lines.append("Есть долг по заказу")
    if items:
        lines.append("Состав:")
        for item in items:
            name = item.get("name") or item.get("dishName") or "Позиция"
            qty = item.get("qty") or 0
            total = float(item.get("sum") or 0)
            lines.append(f"- {name} x{qty}: {total:.2f} руб.")
    return "\n".join(lines)


def format_debts(orders: list[dict[str, Any]]) -> str:
    if not orders:
        return "Долгов по заказам нет."

    total = sum(float(order.get("amount") or 0) for order in orders)
    lines = [
        f"Долги по заказам: {len(orders)}",
        f"Общая сумма: {total:.2f} руб.",
        "",
    ]
    for order in orders[:10]:
        due_date = order.get("debt_payment_date") or order.get("date_issue") or "дата не указана"
        lines.append(
            f"- Заказ #{order.get('orderId')}: {float(order.get('amount') or 0):.2f} руб., "
            f"погасить до {due_date}"
        )
    if len(orders) > 10:
        lines.append(f"Еще долговых заказов: {len(orders) - 10}")
    return "\n".join(lines)


def get_status(vk_user_id: int) -> dict[str, Any]:
    response = backend_get("/api/vk-bot/status", vkUserId=vk_user_id)
    if not response.ok:
        return {"linked": False}
    return response.json()


def handle_message(message: dict[str, Any]) -> None:
    peer_id = message.get("peer_id")
    vk_user_id = message.get("from_id")
    text = (message.get("text") or "").strip()
    if not peer_id or not vk_user_id:
        return

    print(f"VK message received: user={vk_user_id}, text={text[:80]!r}", flush=True)

    lower = text.lower()
    if lower in {"/start", "start", "начать", "меню"}:
        status = get_status(vk_user_id)
        if status.get("linked"):
            send_message(
                peer_id,
                "Профиль привязан. Выберите действие.",
                MAIN_KEYBOARD,
            )
        else:
            send_message(
                peer_id,
                "Профиль пока не привязан. Нажмите «Ввести код» или отправьте 6-значный код от кассира.",
                LINK_KEYBOARD,
            )
        return

    if "ввести код" in lower:
        send_message(peer_id, "Отправьте 6-значный код, который выдал кассир.", LINK_KEYBOARD)
        return

    code = normalize_code(text)
    if code:
        response = backend_post(
            "/api/vk-bot/link/confirm",
            json={"vkUserId": vk_user_id, "code": code},
        )
        if response.ok:
            data = response.json()
            send_message(
                peer_id,
                f"Готово, профиль привязан к клиенту: {data.get('clientName') or data.get('clientId')}.",
                MAIN_KEYBOARD,
            )
        else:
            send_message(peer_id, "Код не подошел или истек. Попросите кассира выдать новый код.", LINK_KEYBOARD)
        return

    if "послед" in lower:
        response = backend_get("/api/vk-bot/orders/latest", vkUserId=vk_user_id)
        if response.ok:
            send_message(peer_id, format_order(response.json()), MAIN_KEYBOARD)
        else:
            keyboard_json = MAIN_KEYBOARD if get_status(vk_user_id).get("linked") else LINK_KEYBOARD
            send_message(peer_id, "Профиль не привязан или заказов пока нет.", keyboard_json)
        return

    if "истор" in lower or "заказ" in lower:
        response = backend_get("/api/vk-bot/orders/history", vkUserId=vk_user_id, limit=5)
        if response.ok:
            orders = response.json()
            if not orders:
                send_message(peer_id, "Заказов пока нет.", MAIN_KEYBOARD)
            else:
                summary = "\n\n".join(format_order(order) for order in orders)
                send_message(peer_id, summary[:3500], MAIN_KEYBOARD)
        else:
            send_message(peer_id, "Профиль не привязан. Введите код, который выдал кассир.", LINK_KEYBOARD)
        return

    if "долг" in lower:
        response = backend_get("/api/vk-bot/orders/debts", vkUserId=vk_user_id)
        if response.ok:
            send_message(peer_id, format_debts(response.json())[:3500], MAIN_KEYBOARD)
        else:
            send_message(peer_id, "Профиль не привязан. Введите код, который выдал кассир.", LINK_KEYBOARD)
        return

    if "отвяз" in lower:
        response = backend_post("/api/vk-bot/unlink", vkUserId=vk_user_id)
        send_message(
            peer_id,
            "Профиль отвязан." if response.ok else "Не удалось отвязать профиль.",
            LINK_KEYBOARD if response.ok else MAIN_KEYBOARD,
        )
        return

    status = get_status(vk_user_id)
    if status.get("linked"):
        send_message(peer_id, "Выберите действие на клавиатуре.", MAIN_KEYBOARD)
    else:
        send_message(peer_id, "Отправьте 6-значный код, который выдал кассир.", LINK_KEYBOARD)


def poll_loop() -> None:
    retry_delay = 10
    while True:
        try:
            server = get_long_poll_server()
            break
        except Exception as exc:
            if is_long_poll_disabled_error(exc):
                print(
                    "VK Long Poll is disabled for this group. "
                    "Enable it in VK community settings, then the bot will continue automatically.",
                    flush=True,
                )
                time.sleep(60)
                continue
            print(f"VK long poll server unavailable: {exc}", flush=True)
            time.sleep(retry_delay)
            retry_delay = min(retry_delay * 2, 60)

    print("VK bot started and is waiting for messages.", flush=True)
    while True:
        try:
            response = HTTP.get(
                server["server"],
                params={"act": "a_check", "key": server["key"], "ts": server["ts"], "wait": POLL_WAIT},
                timeout=POLL_WAIT + 10,
            )
            data = response.json()
            if "failed" in data:
                print(f"VK long poll failed={data.get('failed')}, refreshing server.", flush=True)
                server = get_long_poll_server()
                continue
            server["ts"] = data["ts"]
            updates = data.get("updates", [])
            if updates:
                print(
                    "VK updates received: "
                    + ", ".join(str(update.get("type")) for update in updates),
                    flush=True,
                )
            for update in updates:
                if update.get("type") == "message_new":
                    handle_message(update.get("object", {}).get("message", {}))
        except Exception as exc:
            print(f"VK bot error: {exc}", flush=True)
            time.sleep(5)
            try:
                server = get_long_poll_server()
            except Exception as refresh_exc:
                print(f"VK long poll refresh error: {refresh_exc}", flush=True)
                time.sleep(10)


if __name__ == "__main__":
    if not GROUP_TOKEN or not GROUP_ID:
        print("VK_GROUP_TOKEN and VK_GROUP_ID are not set. VK bot is idle.", flush=True)
        while True:
            time.sleep(3600)
    poll_loop()
