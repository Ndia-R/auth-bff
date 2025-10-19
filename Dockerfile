# ====================================
# 開発環境ステージ
# ====================================
FROM eclipse-temurin:17-jdk-jammy AS development

RUN apt-get update && \
    apt-get install -y git curl sudo bash python3 && \
    curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

# vscodeユーザーを作成
RUN useradd -m vscode

# vscodeユーザーがパスワードなしでsudoを使えるように設定
# /etc/sudoers.d/vscodeファイルを作成し、NOPASSWD: ALL を設定
RUN echo "vscode ALL=(ALL) NOPASSWD: ALL" > /etc/sudoers.d/vscode && \
    chmod 0440 /etc/sudoers.d/vscode

# vscodeユーザーに切り替え
USER vscode
WORKDIR /workspace

# Gradleキャッシュ用ディレクトリを作成（volume用）
RUN mkdir -p /home/vscode/.gradle

# Python uv（Serena MCP用）をインストール
RUN curl -LsSf https://astral.sh/uv/install.sh | sh

# uvをPATHに追加
ENV PATH="/home/vscode/.local/bin:$PATH"

# Gemini CLIをグローバルインストール
USER root
RUN npm install -g @google/gemini-cli
# 元のユーザーに戻す
USER vscode