#!/usr/bin/env python3
"""Генерирует deploy/vm/initdb/02-seed.sql — выдуманные тестовые данные для CafeHelp.

Все имена, телефоны и адреса вымышлены. Запуск: python3 gen_seed.py > ../initdb/02-seed.sql
Данные строятся относительно даты запуска seed (SQL использует current_date),
поэтому история заказов всегда заканчивается "сегодня".
"""
import random

R = random.Random(20261113)
DAYS = 60

ADMIN_HASH = "$2b$10$a9LNGVr9W7TE6qVF2VAMAuHtSn/cm1slSZUmIbdpYOgMsbt7/6KUW"   # admin123
KASSIR_HASH = "$2b$10$atyci26kP0kDIEXsqJw54u8bc05I1SHwiPE0nUGRjYxiHruE8jOUe"  # kassir123

out = []
w = out.append


def q(s):
    return "NULL" if s is None else "'" + str(s).replace("'", "''") + "'"


suppliers = [
    (1, "ООО «Морепродукты Тест»", "+7 900 000-01-01, manager@seafood.test"),
    (2, "ИП Овощи-Фрукты (тест)", "+7 900 000-01-02"),
    (3, "Бакалея Опт (тест)", "bakaleya@example.test"),
    (4, "Напитки и упаковка (тест)", "+7 900 000-01-04"),
]

# id, name, price за unit, unit, base_unit, factor, supplier, type, stock (в unit)
products = [
    (1, "Рис для суши", 180, "kg", "g", 1000, 3, "ingredient", 40),
    (2, "Нори листы", 9, "pcs", "pcs", 1, 3, "ingredient", 600),
    (3, "Лосось охлаждённый", 1650, "kg", "g", 1000, 1, "ingredient", 12),
    (4, "Тунец", 1900, "kg", "g", 1000, 1, "ingredient", 5),
    (5, "Угорь копчёный", 2400, "kg", "g", 1000, 1, "ingredient", 4),
    (6, "Креветка тигровая", 1300, "kg", "g", 1000, 1, "ingredient", 6),
    (7, "Снежный краб", 520, "kg", "g", 1000, 1, "ingredient", 8),
    (8, "Сыр сливочный", 850, "kg", "g", 1000, 3, "ingredient", 10),
    (9, "Огурец", 160, "kg", "g", 1000, 2, "ingredient", 8),
    (10, "Авокадо", 650, "kg", "g", 1000, 2, "ingredient", 5),
    (11, "Икра масаго", 1400, "kg", "g", 1000, 1, "ingredient", 2),
    (12, "Кунжут", 420, "kg", "g", 1000, 3, "ingredient", 3),
    (13, "Соус унаги", 520, "l", "ml", 1000, 3, "ingredient", 5),
    (14, "Соус соевый", 260, "l", "ml", 1000, 3, "ingredient", 20),
    (15, "Майонез японский", 480, "kg", "g", 1000, 3, "ingredient", 6),
    (16, "Соус чили", 390, "l", "ml", 1000, 3, "ingredient", 3),
    (17, "Уксус рисовый", 310, "l", "ml", 1000, 3, "ingredient", 6),
    (18, "Сахар", 85, "kg", "g", 1000, 3, "ingredient", 10),
    (19, "Мука темпурная", 290, "kg", "g", 1000, 3, "ingredient", 8),
    (20, "Панировка панко", 340, "kg", "g", 1000, 3, "ingredient", 6),
    (21, "Масло фритюрное", 190, "l", "ml", 1000, 3, "ingredient", 20),
    (22, "Мука пшеничная", 60, "kg", "g", 1000, 3, "ingredient", 25),
    (23, "Сыр моцарелла", 780, "kg", "g", 1000, 3, "ingredient", 10),
    (24, "Соус томатный", 240, "kg", "g", 1000, 3, "ingredient", 8),
    (25, "Пепперони", 990, "kg", "g", 1000, 1, "ingredient", 4),
    (26, "Куриное филе", 430, "kg", "g", 1000, 1, "ingredient", 10),
    (27, "Шампиньоны", 320, "kg", "g", 1000, 2, "ingredient", 5),
    (28, "Лапша удон", 260, "kg", "g", 1000, 3, "ingredient", 8),
    (29, "Мисо паста", 690, "kg", "g", 1000, 3, "ingredient", 3),
    (30, "Тофу", 380, "kg", "g", 1000, 3, "ingredient", 3),
    (31, "Водоросли вакаме", 1200, "kg", "g", 1000, 3, "ingredient", 1),
    (32, "Салат айсберг", 290, "kg", "g", 1000, 2, "ingredient", 4),
    (33, "Помидоры черри", 450, "kg", "g", 1000, 2, "ingredient", 4),
    (34, "Яйцо куриное", 11, "pcs", "pcs", 1, 2, "ingredient", 180),
    (35, "Дрожжи сухие", 900, "kg", "g", 1000, 3, "ingredient", 1),
    (36, "Кола 0,5 л", 65, "pcs", "pcs", 1, 4, "ingredient", 120),
    (37, "Морс клюквенный", 140, "l", "ml", 1000, 4, "ingredient", 20),
    (38, "Чай зелёный", 1500, "kg", "g", 1000, 4, "ingredient", 2),
    (39, "Мороженое пломбир", 520, "kg", "g", 1000, 4, "ingredient", 6),
    (40, "Имбирь маринованный", 380, "kg", "g", 1000, 3, "ingredient", 6),
    (41, "Васаби", 900, "kg", "g", 1000, 3, "ingredient", 2),
    (42, "Палочки для еды", 2.5, "pcs", "pcs", 1, 4, "consumable", 2000),
    (43, "Контейнер для роллов", 12, "pcs", "pcs", 1, 4, "packaging", 800),
    (44, "Пакет фирменный", 6, "pcs", "pcs", 1, 4, "packaging", 900),
]
P = {p[0]: p for p in products}

# Полуфабрикаты: id, name, output_weight (г), [(productid, г)], склад (г)
preps = [
    (1, "Рис для суши (готовый)", 1000, [(1, 650), (17, 60), (18, 25)], 8000),
    (2, "Соус спайси", 500, [(15, 400), (16, 100)], 2500),
    (3, "Тесто для пиццы", 1000, [(22, 600), (35, 10), (18, 10)], 6000),
]

categories = [(1, "Роллы"), (2, "Запечённые роллы"), (3, "Суши"), (4, "Супы"),
              (5, "Салаты"), (6, "Пицца"), (7, "Горячее"), (8, "Напитки"), (9, "Десерты")]

# Блюда: name, category_id, weight (г), price, техкарта [(kind, id, г)] kind: p=продукт, s=полуфабрикат
R_ = "s"
dishes = [
    ("Филадельфия классик", 1, 260, 590, [(R_, 1, 120), ("p", 2, 1), ("p", 3, 90), ("p", 8, 50)]),
    ("Филадельфия с огурцом", 1, 270, 620, [(R_, 1, 120), ("p", 2, 1), ("p", 3, 80), ("p", 8, 45), ("p", 9, 25)]),
    ("Калифорния с крабом", 1, 250, 450, [(R_, 1, 120), ("p", 2, 1), ("p", 7, 60), ("p", 10, 30), ("p", 11, 15), ("p", 15, 15)]),
    ("Калифорния с креветкой", 1, 250, 520, [(R_, 1, 120), ("p", 2, 1), ("p", 6, 55), ("p", 10, 30), ("p", 11, 15)]),
    ("Канада", 1, 260, 610, [(R_, 1, 120), ("p", 2, 1), ("p", 5, 50), ("p", 3, 30), ("p", 8, 40), ("p", 13, 15), ("p", 12, 3)]),
    ("Ролл с лососем", 1, 120, 290, [(R_, 1, 80), ("p", 2, 1), ("p", 3, 40)]),
    ("Ролл с тунцом", 1, 120, 310, [(R_, 1, 80), ("p", 2, 1), ("p", 4, 40)]),
    ("Ролл с огурцом", 1, 110, 160, [(R_, 1, 80), ("p", 2, 1), ("p", 9, 30), ("p", 12, 2)]),
    ("Ролл с угрём", 1, 120, 330, [(R_, 1, 80), ("p", 2, 1), ("p", 5, 35), ("p", 13, 10)]),
    ("Зелёный дракон", 1, 270, 640, [(R_, 1, 120), ("p", 2, 1), ("p", 5, 50), ("p", 10, 50), ("p", 8, 30), ("p", 13, 15)]),
    ("Запечённый с лососем", 2, 280, 520, [(R_, 1, 130), ("p", 2, 1), ("p", 3, 50), ("p", 8, 30), (R_, 2, 40)]),
    ("Запечённый с крабом", 2, 280, 440, [(R_, 1, 130), ("p", 2, 1), ("p", 7, 60), ("p", 8, 30), (R_, 2, 40)]),
    ("Запечённый с курицей", 2, 280, 410, [(R_, 1, 130), ("p", 2, 1), ("p", 26, 60), ("p", 8, 30), (R_, 2, 40)]),
    ("Темпура с креветкой", 2, 290, 560, [(R_, 1, 130), ("p", 2, 1), ("p", 6, 50), ("p", 8, 30), ("p", 19, 30), ("p", 21, 40)]),
    ("Темпура с лососем", 2, 290, 570, [(R_, 1, 130), ("p", 2, 1), ("p", 3, 50), ("p", 8, 30), ("p", 20, 25), ("p", 21, 40)]),
    ("Горячий ролл с угрём", 2, 290, 610, [(R_, 1, 130), ("p", 2, 1), ("p", 5, 45), ("p", 8, 30), ("p", 19, 30), ("p", 21, 40), ("p", 13, 15)]),
    ("Суши с лососем", 3, 40, 120, [(R_, 1, 25), ("p", 3, 15)]),
    ("Суши с тунцом", 3, 40, 130, [(R_, 1, 25), ("p", 4, 15)]),
    ("Суши с угрём", 3, 40, 140, [(R_, 1, 25), ("p", 5, 15), ("p", 13, 5)]),
    ("Суши с креветкой", 3, 40, 130, [(R_, 1, 25), ("p", 6, 15)]),
    ("Суши с томаго", 3, 45, 100, [(R_, 1, 25), ("p", 34, 1), ("p", 2, 1)]),
    ("Гункан спайси лосось", 3, 50, 150, [(R_, 1, 25), ("p", 2, 1), ("p", 3, 15), (R_, 2, 10)]),
    ("Мисо суп", 4, 300, 220, [("p", 29, 25), ("p", 30, 40), ("p", 31, 5)]),
    ("Том ям с креветками", 4, 350, 490, [("p", 6, 60), ("p", 27, 40), ("p", 33, 30), ("p", 16, 15)]),
    ("Суп с лапшой удон", 4, 350, 330, [("p", 28, 120), ("p", 26, 60), ("p", 34, 1), ("p", 14, 20)]),
    ("Салат с креветкой", 5, 220, 390, [("p", 6, 60), ("p", 32, 80), ("p", 33, 40), (R_, 2, 20)]),
    ("Салат чука", 5, 150, 280, [("p", 31, 60), ("p", 12, 5), ("p", 9, 30)]),
    ("Салат с курицей", 5, 230, 320, [("p", 26, 80), ("p", 32, 80), ("p", 33, 40), ("p", 15, 20)]),
    ("Пицца Маргарита", 6, 520, 490, [(R_, 3, 250), ("p", 24, 80), ("p", 23, 150), ("p", 33, 40)]),
    ("Пицца Пепперони", 6, 540, 590, [(R_, 3, 250), ("p", 24, 80), ("p", 23, 150), ("p", 25, 70)]),
    ("Пицца с курицей и грибами", 6, 560, 610, [(R_, 3, 250), ("p", 24, 70), ("p", 23, 140), ("p", 26, 80), ("p", 27, 60)]),
    ("Удон с курицей", 7, 350, 420, [("p", 28, 150), ("p", 26, 90), ("p", 9, 30), ("p", 14, 30)]),
    ("Удон с креветкой", 7, 350, 490, [("p", 28, 150), ("p", 6, 80), ("p", 9, 30), ("p", 14, 30)]),
    ("Креветки в темпуре", 7, 180, 450, [("p", 6, 120), ("p", 19, 40), ("p", 21, 60), (R_, 2, 20)]),
    ("Куриные наггетсы", 7, 200, 290, [("p", 26, 150), ("p", 20, 40), ("p", 21, 60), ("p", 34, 1)]),
    ("Кола 0,5 л", 8, 500, 120, [("p", 36, 1)]),
    ("Морс клюквенный 0,4 л", 8, 400, 140, [("p", 37, 400)]),
    ("Чай зелёный чайник", 8, 500, 180, [("p", 38, 8)]),
    ("Мороженое пломбир", 9, 100, 150, [("p", 39, 100)]),
    ("Моти клубника (шт)", 9, 50, 130, [("p", 39, 30), ("p", 18, 10)]),
]

# Сеты: name, price, [(dish index 1-based, qty)]
sets = [
    ("Сет «Филадельфия»", 1690, [(1, 2), (2, 1)]),
    ("Сет «Классика»", 1290, [(6, 1), (7, 1), (8, 1), (9, 1), (3, 1)]),
    ("Сет «Горячий»", 1890, [(11, 1), (12, 1), (14, 1), (16, 1)]),
    ("Сет «Компания»", 3290, [(1, 1), (3, 1), (5, 1), (10, 1), (11, 1), (14, 1)]),
    ("Сет «Суши-ассорти»", 990, [(17, 2), (18, 2), (19, 2), (20, 2)]),
    ("Сет «Обед»", 690, [(6, 1), (23, 1), (37, 1)]),
]

persons = [
    (1, "Иванов Тест Админович", 0, "admin", ADMIN_HASH, "OWNER"),
    (2, "Петрова Тест Кассировна", 2200, "kassir", KASSIR_HASH, "WORKER"),
    (3, "Смирнов Тест Поварович", 2600, None, None, None),
    (4, "Козлова Тест Сушефовна", 2800, None, None, None),
    (5, "Орлов Тест Курьерович", 1800, None, None, None),
]

first = ["Анна", "Борис", "Виктор", "Галина", "Дмитрий", "Елена", "Жанна", "Игорь", "Ксения", "Леонид",
         "Мария", "Никита", "Ольга", "Павел", "Регина", "Сергей", "Татьяна", "Ульяна", "Фёдор", "Юлия"]
last = ["Тестов", "Примеров", "Демонстров", "Образцов", "Пробнов", "Шаблонов", "Макетов", "Черновиков"]
streets = ["ул. Тестовая", "ул. Примерная", "пр. Демонстрационный", "ул. Образцовая", "пер. Пробный", "ул. Шаблонная"]
clients = []
for i in range(1, 41):
    f = R.choice(first)
    l = R.choice(last)
    if f.endswith("а") or f.endswith("я"):
        l += "а"
    digits = "7900%07d" % (1000 + i)
    clients.append((i, f"{f} {l}", f"+7 900 {digits[4:7]}-{digits[7:9]}-{digits[9:11]}", digits))

# ---------------------------------------------------------------- SQL
w("-- Тестовые данные CafeHelp. Все люди, телефоны и адреса выдуманы.")
w("-- Сгенерировано deploy/vm/tools/gen_seed.py. Применяется к пустой схеме после миграций backend.")
w("BEGIN;")
w("SET search_path = sales, public;")
w("DO $$ BEGIN IF EXISTS (SELECT 1 FROM sales.dish) THEN RAISE EXCEPTION 'База уже содержит блюда - seed не применяется'; END IF; END $$;")

for sid, name, comm in suppliers:
    w(f"INSERT INTO supplier (supplierid, suppliername, communication) OVERRIDING SYSTEM VALUE VALUES ({sid}, {q(name)}, {q(comm)});")

for pid, name, price, unit, bu, f, sup, typ, stock in products:
    w("INSERT INTO product (productid, productname, productprice, waste, isfavourite, supplierid, unit, base_unit, unit_factor, item_type) "
      f"OVERRIDING SYSTEM VALUE VALUES ({pid}, {q(name)}, {price}, 0, {'true' if pid in (1, 3, 8) else 'false'}, {sup}, {q(unit)}, {q(bu)}, {f}, {q(typ)});")
    w(f"INSERT INTO product_supplier (product_id, supplier_id, default_price, purchase_unit, purchase_unit_factor, is_active, last_price_at) "
      f"VALUES ({pid}, {sup}, {price}, {q(unit)}, {f}, true, now() - interval '20 days');")

w("INSERT INTO warehouse (warehouseid, warehousename, is_main) OVERRIDING SYSTEM VALUE VALUES (1, 'Основной склад', true), (2, 'Бар', false);")
for pid, name, price, unit, bu, f, sup, typ, stock in products:
    wh = 2 if pid in (36, 37, 38) else 1
    qty = stock * f
    avg = round(price / f, 6)
    w("INSERT INTO productwarehouse (warehouseid, productid, quantity, inventory_value, average_unit_cost, valuation_initialized) "
      f"VALUES ({wh}, {pid}, {qty}, {round(qty * avg, 6)}, {avg}, true);")

for prid, name, outw, items, stock in preps:
    w(f"INSERT INTO preparation (preparationid, preparationname, output_weight) OVERRIDING SYSTEM VALUE VALUES ({prid}, {q(name)}, {outw});")
    for pid, g in items:
        w(f"INSERT INTO techproduct (preparation_id, productid, weight, waste) VALUES ({prid}, {pid}, {g}, 0);")
prep_cost = {}
for prid, name, outw, items, stock in preps:
    cost = sum(P[pid][2] / P[pid][5] * g for pid, g in items) / outw  # руб/г
    prep_cost[prid] = cost
    w("INSERT INTO preparationwarehouse (warehouseid, preparationid, quantity, inventory_value, average_unit_cost, valuation_initialized) "
      f"VALUES (1, {prid}, {stock}, {round(stock * cost, 6)}, {round(cost, 6)}, true);")

for cid, name in categories:
    w(f"INSERT INTO dish_category (category_id, name) OVERRIDING SYSTEM VALUE VALUES ({cid}, {q(name)});")

dish_cost = {}
for i, (name, cat, weight, price, tc) in enumerate(dishes, start=1):
    cost = 0.0
    for kind, ref, g in tc:
        cost += (P[ref][2] / P[ref][5] * g) if kind == "p" else prep_cost[ref] * g
    cost = round(cost, 2)
    dish_cost[i] = cost
    catname = dict(categories)[cat]
    w("INSERT INTO dish (dishid, dishname, weight, firstcost, price, techproductid, category, category_id) "
      f"OVERRIDING SYSTEM VALUE VALUES ({i}, {q(name)}, {weight}, {cost}, {price}, {i}, {q(catname)}, {cat});")
    for kind, ref, g in tc:
        if kind == "p":
            w(f"INSERT INTO techproduct (\"DishId\", productid, weight, waste) VALUES ({i}, {ref}, {g}, 0);")
        else:
            w(f"INSERT INTO techproduct (\"DishId\", ingredient_preparation_id, weight, waste) VALUES ({i}, {ref}, {g}, 0);")

set_cost = {}
for sidx, (name, price, items) in enumerate(sets, start=1):
    cost = round(sum(dish_cost[d] * n for d, n in items), 2)
    set_cost[sidx] = cost
    w(f"INSERT INTO dish_set (setid, setname, price, first_cost) OVERRIDING SYSTEM VALUE VALUES ({sidx}, {q(name)}, {price}, {cost});")
    for d, n in items:
        w(f"INSERT INTO dish_set_item (set_id, dish_id, qty) VALUES ({sidx}, {d}, {n});")

w("INSERT INTO consumable_rule (product_id, basis, default_quantity, trigger_quantity, active) VALUES (42, 'per_person', 1, 1, true);")

for pid, name, sal, user, h, role in persons:
    w(f"INSERT INTO person (personid, name, salaryperday, archived) OVERRIDING SYSTEM VALUE VALUES ({pid}, {q(name)}, {sal}, false);")
    if user:
        w(f"INSERT INTO user_account (personid, username, password_hash, role, is_active) VALUES ({pid}, {q(user)}, {q(h)}, {q(role)}, true);")

for cid, name, num, norm in clients:
    w(f"INSERT INTO client (clientid, fullname, number, normalized_number) OVERRIDING SYSTEM VALUE VALUES ({cid}, {q(name)}, {q(num)}, {q(norm)});")

# ---------------------------------------------------------------- смены и заказы
# Популярность блюд: роллы и пицца чаще
weights = []
for i, (name, cat, *_r) in enumerate(dishes, start=1):
    weights.append({1: 6, 2: 5, 3: 3, 4: 2, 5: 2, 6: 4, 7: 2, 8: 3, 9: 1}[cat] * R.uniform(0.6, 1.4))

order_id = 0
line_id = 0
oc_id = 0
w("-- Смены и заказы за последние %d дней (день 0 = сегодня, смена открыта)" % DAYS)
for day in range(DAYS, -1, -1):
    shift_id = DAYS - day + 1
    d = f"(current_date - {day})"
    is_today = day == 0
    # день недели в SQL неизвестен заранее, поэтому «выходные» берём по номеру дня
    base = R.randint(14, 24)
    if (day % 7) in (0, 1):
        base += R.randint(6, 12)
    if is_today:
        base = 8
    orders = []
    shift_income = 0.0
    shift_cost = 0.0
    for _ in range(base):
        order_id += 1
        minutes = R.randint(10 * 60 + 5, (21 if not is_today else 13) * 60 + 30)
        lines = []
        if R.random() < 0.18:
            s = R.randint(1, len(sets))
            lines.append(("set", s, 1 if R.random() < 0.85 else 2))
        for _k in range(R.choices([0, 1, 2, 3, 4], [1, 4, 4, 2, 1])[0] if lines else R.choices([1, 2, 3, 4, 5], [3, 4, 3, 2, 1])[0]):
            di = R.choices(range(1, len(dishes) + 1), weights)[0]
            lines.append(("dish", di, R.choices([1, 2, 3], [7, 2, 1])[0]))
        if not lines:
            lines.append(("dish", 1, 1))
        items_total = sum((sets[r - 1][1] if k == "set" else dishes[r - 1][3]) * n for k, r, n in lines)
        cost_total = sum((set_cost[r] if k == "set" else dish_cost[r]) * n for k, r, n in lines)
        delivery = R.random() < 0.3
        delivery_fee = 150 if delivery and items_total < 1500 else 0
        persons_n = max(1, min(6, round(items_total / 700)))
        discount = 0
        if R.random() < 0.08 and items_total >= 800:
            discount = R.choice([50, 100, 150, 200])
        amount = items_total + delivery_fee - discount
        client = R.randint(1, 40) if (delivery or R.random() < 0.3) else None
        debt = (client is not None) and R.random() < 0.03 and not is_today
        pay = R.choices(["cash", "card", "transfer"], [3, 5, 2])[0]
        status = not is_today or R.random() < 0.5
        paid = (not debt) and (not is_today or R.random() < 0.6)
        prep = 30 if not delivery else 60
        created = f"({d} + time '00:00' + interval '{minutes} minutes')"
        ready = f"({created} + interval '{prep} minutes')"
        cash_received = cash_change = "NULL"
        if paid and pay == "cash":
            rec = amount if R.random() < 0.4 else ((int(amount) // 500) + 1) * 500
            cash_received, cash_change = str(rec), str(rec - amount)
        phone = addr = None
        if delivery:
            if client:
                phone = clients[client - 1][2]
            else:
                phone = "+7 900 999-%02d-%02d" % (R.randint(0, 99), R.randint(0, 99))
            addr = f"{R.choice(streets)}, д. {R.randint(1, 120)}, кв. {R.randint(1, 200)}"
        debt_amt = f"{amount}" if debt else "NULL"
        w("INSERT INTO \"order\" (orderid, shiftid, \"Date\", clientid, amount, status, type, time, timedelay, created_at, duty, date_issue, "
          "debt_payment_date, delivery_phone, delivery_address, payment_type, is_paid, debt_original_amount, debt_remaining_amount, "
          "inventory_consumed, paid_at, person_count, cash_received, cash_change, discount_amount, ready_at) OVERRIDING SYSTEM VALUE VALUES ("
          f"{order_id}, {shift_id}, {d}, {client if client else 'NULL'}, {amount}, {str(status).lower()}, {str(delivery).lower()}, {prep}, NULL, {created}, "
          f"{str(debt).lower()}, {d if status else 'NULL'}, {f'({d} + 14)' if debt else 'NULL'}, {q(phone)}, {q(addr)}, "
          f"{q(pay if paid else 'unpaid')}, {str(paid).lower()}, {debt_amt}, {debt_amt}, {str(paid or debt).lower()}, "
          f"{created if paid else 'NULL'}, {persons_n}, {cash_received}, {cash_change}, {discount}, {ready});")
        for k, r, n in lines:
            line_id += 1
            if k == "set":
                w(f"INSERT INTO orderdish (id, orderid, set_id, qty, unit_price, unit_cost) OVERRIDING SYSTEM VALUE VALUES ({line_id}, {order_id}, {r}, {n}, {sets[r - 1][1]}, {set_cost[r]});")
            else:
                w(f"INSERT INTO orderdish (id, orderid, dishid, qty, unit_price, unit_cost) OVERRIDING SYSTEM VALUE VALUES ({line_id}, {order_id}, {r}, {n}, {dishes[r - 1][3]}, {dish_cost[r]});")
        oc_id += 1
        w("INSERT INTO order_consumable (id, order_id, product_id, suggested_quantity, actual_quantity, surcharge_amount, inventory_cost, "
          f"product_name_snapshot, base_unit_snapshot) OVERRIDING SYSTEM VALUE VALUES ({oc_id}, {order_id}, 42, {persons_n}, {persons_n}, 0, "
          f"{round(persons_n * 2.5, 2)}, 'Палочки для еды', 'pcs');")
        if paid:
            shift_income += amount
            shift_cost += cost_total
    endtime = "NULL" if is_today else "time '22:00'"
    closed = "NULL" if is_today else f"({d} + time '22:05')"
    income = round(shift_income, 2)
    profit = round(shift_income - shift_cost, 2)
    # смена вставляется до заказов: выводим её в начало блока через отдельный список
    out.insert(len(out) - 0, "")  # no-op для читаемости
    shift_sql = ("INSERT INTO shift (id, data, starttime, endtime, profit, expenses, income, personcode, closed_at) OVERRIDING SYSTEM VALUE VALUES "
                 f"({shift_id}, {d}, time '10:00', {endtime}, {'NULL' if is_today else profit}, {'NULL' if is_today else round(shift_cost, 2)}, "
                 f"{'NULL' if is_today else income}, 2, {closed});")
    # найти первую строку заказов этой смены и вставить смену перед ней
    marker = f"OVERRIDING SYSTEM VALUE VALUES ({order_id - base + 1}, {shift_id},"
    for idx in range(len(out) - 1, -1, -1):
        if marker in out[idx]:
            out.insert(idx, shift_sql)
            break
    staff = [2, R.choice([3, 4])] + ([5] if R.random() < 0.6 else [])
    for p_ in staff:
        w(f"INSERT INTO shiftperson (shiftid, personid) VALUES ({shift_id}, {p_});")

# Синхронизация последовательностей / identity
for table, col in [("supplier", "supplierid"), ("product", "productid"), ("warehouse", "warehouseid"),
                   ("preparation", "preparationid"), ("dish_category", "category_id"), ("dish", "dishid"),
                   ("dish_set", "setid"), ("person", "personid"), ("client", "clientid"), ("shift", "id"),
                   ("\"order\"", "orderid"), ("orderdish", "id"), ("order_consumable", "id"),
                   ("techproduct", "techproductid"), ("dish_set_item", "set_item_id"),
                   ("productwarehouse", "productwarehouseid"), ("preparationwarehouse", "preparationwarehouseid"),
                   ("user_account", "id"), ("shiftperson", "shiftpersonid")]:
    w(f"SELECT setval(pg_get_serial_sequence('sales.{table}', '{col}'), (SELECT COALESCE(MAX({col}), 1) FROM sales.{table}));")

w("COMMIT;")
print("\n".join(out))
