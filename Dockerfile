FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update && \
    apt-get install -y git curl sudo bash python3 python3-pip && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

# 既存ユーザーがいないので、新規作成
RUN useradd -m vscode

# rootユーザーで作業ディレクトリの所有者とグループを変更
USER root
WORKDIR /workspace
RUN chown vscode:vscode /workspace

# ユーザー変更
USER vscode

# .gradleディレクトリはvolumeとしてバインドするので
# そのためのディレクトリをあらかじめ作成しておく
RUN mkdir -p /home/vscode/.gradle && \
    chown -R vscode:vscode /home/vscode/.gradle

# vscodeユーザー用のnpmグローバルディレクトリを設定
RUN mkdir ~/.npm-global && \
    npm config set prefix '~/.npm-global' && \
    echo 'export PATH=~/.npm-global/bin:$PATH' >> ~/.bashrc

# Claude Codeをvscodeユーザーでインストール
RUN npm install -g @anthropic-ai/claude-code

# Python uvをvscodeユーザーでインストール（Serena MCP用）
RUN curl -LsSf https://astral.sh/uv/install.sh | sh

# vscodeユーザーのPATHにuvとnpm globalを追加
ENV PATH="/home/vscode/.npm-global/bin:/home/vscode/.local/bin:$PATH"
RUN echo 'export PATH="/home/vscode/.npm-global/bin:/home/vscode/.local/bin:$PATH"' >> /home/vscode/.bashrc