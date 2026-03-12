import os  
needle = 'getDish'  
root = r'src'  
for base, dirs, files in os.walk(root):  
    for name in files:  
        if name.endswith(('.java','.kt','.js','.ts')):  
            path = os.path.join(base, name)  
            try:  
                data = open(path, encoding='utf-8').read()  
            except Exception:  
                continue  
            if needle in data:  
                print(path)  
