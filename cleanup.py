import os
import re

EXCLUDE_DIRS = {'node_modules', 'target', 'venv', '.git', '.idea', 'dist', 'scratch'}
EXTENSIONS = {'.java', '.py', '.js', '.jsx', '.yml', '.css', '.sh', '.properties'}

def is_targeted_comment(line, ext):
    stripped = line.strip()
    if ext in ['.java', '.js', '.jsx', '.css']:
        return stripped.startswith('//')
    elif ext in ['.py', '.yml', '.sh', '.properties']:
        return stripped.startswith('#')
    return False

def process_file(filepath):
    ext = os.path.splitext(filepath)[1]
    with open(filepath, 'r') as f:
        lines = f.readlines()

    new_lines = []
    at_top = True
    in_block_comment = False
    removed_count = 0

    for line in lines:
        stripped = line.strip()
        
        if ext in ['.java', '.js', '.jsx', '.css']:
            if stripped.startswith('/*'):
                in_block_comment = True
            
            if in_block_comment:
                new_lines.append(line)
                if '*/' in stripped:
                    in_block_comment = False
                continue

        if stripped != "":
            if not is_targeted_comment(line, ext) and not in_block_comment:
                if ext == '.java' and (stripped.startswith('package ') or stripped.startswith('import ') or stripped.startswith('@')):
                    pass
                elif ext in ['.js', '.jsx'] and stripped.startswith('import '):
                    pass
                else:
                    at_top = False

        if is_targeted_comment(line, ext):
            if at_top:
                new_lines.append(line)
            else:
                removed_count += 1
        else:
            new_lines.append(line)

    if removed_count > 0:
        with open(filepath, 'w') as f:
            f.writelines(new_lines)
        print(f"Analyzed {filepath}: Removed {removed_count} historical inline comments.")

print("Starting comprehensive comment cleanup file by file...")
for root, dirs, files in os.walk('.'):
    dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
    for file in files:
        ext = os.path.splitext(file)[1]
        if ext in EXTENSIONS:
            filepath = os.path.join(root, file)
            process_file(filepath)

print("Comprehensive comment cleanup finished successfully!")
