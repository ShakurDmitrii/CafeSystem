path = r'frontend/src/pages/SuppliersPage/CashierPages/CashierPage.jsx'  
lines = open(path, encoding='utf-8').read().splitlines()  
patterns = ['createOrder','paymentType','printOrder','deliveryPhone','deliveryAddress']  
ctx = 5  
for p in patterns:  
    for i, l in enumerate(lines):  
        if p in l:  
            s = max(0, i-ctx); e = min(len(lines), i+ctx+1)  
            print('---', p, 'line', i+1)  
            for j in range(s, e):  
                print('{:04d}: {}'.format(j+1, lines[j]))  
