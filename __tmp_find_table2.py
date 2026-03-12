import os,re  
path=os.path.join('db',' backup_sales.sql')  
data=open(path,encoding='utf-8',errors='ignore').read()  
for pat in [r'CREATE TABLE[;]*\"order\"[;]*;', r'CREATE TABLE[;]*order[;]*;'] :  
    m=re.search(pat, data, re.IGNORECASE)  
    print('pattern', pat, 'found', bool(m))  
    if m: print(m.group(0)[:2000]); break  
