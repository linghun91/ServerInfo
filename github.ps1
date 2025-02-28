
# 初始化Git仓库
git init

# 创建.gitignore文件（可选，但推荐）
echo "target/" > .gitignore
echo "*.class" >> .gitignore
echo "*.jar" >> .gitignore
echo "*.log" >> .gitignore
echo ".vscode/" >> .gitignore

# 添加所有文件到暂存区
git add .

# 提交更改
git commit -m "初始提交"

# 添加远程仓库（替换为您的GitHub用户名和仓库名）
git remote add origin https://github.com/linghun91/ServerInfo.git

# 推送到GitHub
git push -u origin master