"""Exercise a packaged MESHFOSS executable under Xvfb with a temporary demo profile."""

import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen


class Browser:
    def __init__(self, port, binary):
        self.address = f"http://127.0.0.1:{port}"
        self.binary = str(binary.resolve())
        self.session = ""

    def request(self, method, path, payload=None):
        body = None if payload is None else json.dumps(payload).encode()
        req = Request(self.address + path, data=body, method=method,
                      headers={"Content-Type": "application/json"})
        try:
            with urlopen(req, timeout=45) as response:
                return json.load(response)["value"]
        except HTTPError as error:
            raise RuntimeError(error.read().decode()) from error

    def open(self):
        capabilities = {"webkitgtk:browserOptions": {"binary": self.binary}}
        result = self.request("POST", "/session", {"capabilities": {"alwaysMatch": capabilities}})
        self.session = "/session/" + result["sessionId"]
        self.request("POST", self.session + "/window/rect", {"width": 1220, "height": 800})

    def close(self):
        if self.session:
            self.request("DELETE", self.session)
            self.session = ""

    def script(self, source, *args):
        return self.request("POST", self.session + "/execute/sync", {"script": source, "args": args})

    def wait(self, check):
        for _ in range(150):
            if value := check():
                return value
            time.sleep(0.15)
        raise AssertionError("Unexpected screen: " + self.script("return document.body.innerText")[:800])

    def click(self, label):
        element = self.wait(lambda: self.script("""
            return [...document.querySelectorAll('button')].find(b => b.offsetWidth && !b.disabled &&
                (b.textContent.trim() === arguments[0] || b.getAttribute('aria-label') === arguments[0])) || null
        """, label))
        element_id = element["element-6066-11e4-a52e-4f735466cecf"]
        self.request("POST", self.session + f"/element/{element_id}/click", {})

    def open_first_row(self):
        element = self.wait(lambda: self.script("return document.querySelector('.record-list .record')"))
        element_id = element["element-6066-11e4-a52e-4f735466cecf"]
        self.request("POST", self.session + f"/element/{element_id}/click", {})

    def expect(self, text):
        self.wait(lambda: self.script("return document.body.innerText.includes(arguments[0])", text))


def exercise(ui):
    ui.open()
    ui.click("Открыть демоверсию")
    ui.expect("Алгебра")
    ui.open_first_row()
    ui.click("Домашнее задание")
    ui.click("Отметить выполненным")
    ui.expect("Снять отметку")
    ui.wait(lambda: ui.script("return !document.querySelector('.detail .primary').disabled"))
    ui.close()

    ui.open()
    ui.expect("Алгебра")
    ui.open_first_row()
    ui.click("Домашнее задание")
    ui.expect("Снять отметку")
    for tab, expected in [("Оценки", "Средневзвешенный"), ("Итоговые", "Алгебра"),
                          ("Предметы", "Физика"), ("Рейтинг", "Медиана"),
                          ("Месяц", "Анна Л."), ("Питание", "Баланс"),
                          ("Предзаказы", "Горячий обед"), ("Москвёнок", "Демонстрационная карта"),
                          ("Настройки", "Канал обновлений")]:
        ui.click(tab)
        ui.expect(expected)
    ui.script("""
        const language = document.querySelector('.language');
        language.value = 'en';
        language.dispatchEvent(new Event('change', {bubbles: true}));
    """)
    ui.click("Schedule")
    ui.expect("Lessons")
    ui.request("POST", ui.session + "/window/rect", {"width": 590, "height": 670})
    ui.open_first_row()
    ui.wait(lambda: ui.script("return getComputedStyle(document.querySelector('.list-col')).display==='none'"))
    assert ui.script("return document.documentElement.scrollWidth <= innerWidth")
    ui.request("POST", ui.session + "/actions", {"actions": [{"type": "key", "id": "keyboard", "actions": [
        {"type": "keyDown", "value": "\ue00c"}, {"type": "keyUp", "value": "\ue00c"}]}]})
    ui.wait(lambda: ui.script("return !document.querySelector('.detail')"))
    ui.click("Settings")
    ui.click("Sign out")
    ui.expect("Sign in")


def main(binary):
    with tempfile.TemporaryDirectory(prefix="mesh-desktop-test-") as temp:
        env = dict(os.environ, TAURI_WEBVIEW_AUTOMATION="true", LIBGL_ALWAYS_SOFTWARE="1",
                   WEBKIT_DISABLE_DMABUF_RENDERER="1")
        for name in ("CONFIG", "DATA", "CACHE"):
            env[f"XDG_{name}_HOME"] = str(Path(temp) / name.lower())
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            port = listener.getsockname()[1]
        driver = subprocess.Popen(["WebKitWebDriver", f"--port={port}"], env=env)
        ui = Browser(port, binary)
        try:
            for _ in range(50):
                try:
                    if ui.request("GET", "/status"):
                        break
                except OSError:
                    time.sleep(0.1)
            exercise(ui)
            print("Packaged desktop walkthrough passed.")
        finally:
            try:
                ui.close()
            finally:
                driver.terminate()
                driver.wait(10)


if __name__ == "__main__":
    main(Path(sys.argv[1]))
