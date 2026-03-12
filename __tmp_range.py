path = r'frontend/src/pages/SuppliersPage/CashierPages/CashierPage.jsx'  
lines = open(path, encoding='utf-8').read().splitlines()  
def show(a,b):  
    for i in range(a-1,b):  
        print('{:04d}: {}'.format(i+1, lines[i]))  
show(470,610)  
print('--- UI ---')  
show(1220,1335)  
print('--- print ticket ---')  
show(700,820)  
