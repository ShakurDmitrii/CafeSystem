import os  
path=os.path.join('db',' backup_sales.sql')  
for line in open(path,encoding='utf-8',errors='ignore'):  
    if 'CREATE TABLE' in line.upper() and 'ORDER' in line.upper():  
        print(line.strip()[:300])  
