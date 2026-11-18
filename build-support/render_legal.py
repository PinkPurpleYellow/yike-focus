from pathlib import Path
from html import escape
base = Path(__file__).resolve().parents[1]
out = base / "docs"
out.mkdir(exist_ok=True)
nav = '<nav><a href="privacy-policy.html">隐私政策</a> · <a href="user-agreement.html">用户协议</a> · <a href="permissions.html">权限用途</a></nav>'
def write(name, title, body):
    page = f'<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{escape(title)}</title><style>body{{max-width:45em;margin:3em auto;padding:0 1.3em;background:#faf6ed;color:#243229;font:17px/1.8 system-ui,sans-serif}}h1{{line-height:1.3}}a{{color:#904936}}article{{white-space:pre-line}}</style><main>{nav}<h1>{escape(title)}</h1><article>{escape(body)}</article></main></html>'
    (out / name).write_text(page, 'utf8')
for asset, name in [('privacy_policy','privacy-policy'), ('user_agreement','user-agreement'), ('permissions','permissions')]:
    title, body = (base/'app/src/main/assets'/f'{asset}.txt').read_text('utf-8-sig').split('\n', 1)
    write(name+'.html', title, body.strip())
write('index.html','一刻专注','离线专注计时与任务记录。\n开发者 PinkPurpleYellow\n联系邮箱 ff1921570609@outlook.com')
