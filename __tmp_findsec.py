import os  
root = r'src'  
for base, dirs, files in os.walk(root):  
    for name in files:  
        if 'Security' in name and name.endswith('.java'):  
            print(os.path.join(base, name))  
