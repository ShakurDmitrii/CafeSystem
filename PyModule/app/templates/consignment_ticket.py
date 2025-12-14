def build_ticket(data):
    lines = []

    lines.append(f"НАКЛАДНАЯ № {data.consignmentId}")
    lines.append(f"Поставщик: {data.supplierName}")
    lines.append(f"Дата: {data.date}")
    lines.append("-" * 32)

    for item in data.items:
        lines.append(item.name)
        lines.append(f"{item.quantity} x {item.price} = {item.sum}")

    lines.append("-" * 32)
    lines.append(f"ИТОГО: {data.total}")

    return lines
