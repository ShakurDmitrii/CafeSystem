import io  
path = r'frontend/src/pages/SuppliersPage/CashierPages/CashierPage.jsx'  
lines = open(path, encoding='utf-8').read().splitlines()  
start=520; end=590  
for i in range(start, end):  
    print(f'{i+1:04d}: {lines[i]}')  
