import re,os  
path=os.path.join('db',' backup_sales.sql')  
data=open(path,encoding='utf-8',errors='ignore').read()  
m=re.search(r'CREATE TABLE[;]*\border\b[;]*;', data, re.IGNORECASE)  
print(m.group(0)[:2000] if m else 'not found')  
