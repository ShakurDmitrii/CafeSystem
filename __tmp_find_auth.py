import os  
needle='Authorization'  
root = r'frontend/src'  
for base, dirs, files in os.walk(root):  
    for name in files:  
        if name.endswith(('.js','.jsx','.ts','.tsx')):  
            path=os.path.join(base,name)  
            try: data=open(path,encoding='utf-8').read()  
            except Exception: continue  
            if needle in data:  
                print(path)  
