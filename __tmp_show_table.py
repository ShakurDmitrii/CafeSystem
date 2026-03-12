import os  
path=os.path.join('db',' backup_sales.sql')  
data=open(path,encoding='utf-8',errors='ignore').read()  
pat='CREATE TABLE sales.\"order\"'  
idx=data.find(pat)  
print(data[idx:idx+3000] if idx!=-1 else 'not found')  
