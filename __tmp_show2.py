import io  
path = r'frontend/src/pages/SuppliersPage/CashierPages/CashierPage.jsx'  
lines = open(path, encoding='utf-8').read().splitlines()  
start=588; end=630  
for i in range(start, end):  
    print(f'{i+1:04d}: {lines[i]}')  
