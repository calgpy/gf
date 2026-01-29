import json
import re
import sys
from datetime import datetime
from playwright.sync_api import sync_playwright

def scrape_match():
    data = {
        "title": "No hay partidos detectados hoy",
        "url": "",
        "date": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "error": None
    }

    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page()
            
            # 1. Ir a la pagina principal
            print("Navegando a goatfutbol.online...")
            page.goto("https://www.goatfutbol.online/", timeout=60000)
            page.wait_for_load_state("networkidle")

            # 2. Buscar todos los enlaces de partidos
            print("Buscando partidos...")
            
            matches_found = []
            
            # Buscamos todos los links que contengan "vs" o estructura de fecha
            # Ajustamos el selector para ser mas abarcativo o especifico segun la pagina
            # Tomamos todos los <a> que tengan href
            candidates = page.locator("a[href*='html']").all()

            unique_links = set()
            
            for link in candidates:
                href = link.get_attribute("href")
                text = link.inner_text().strip()
                
                # Intentar sacar titulo de la imagen si no hay texto
                if not text:
                    img = link.locator("img").first
                    if img.count() > 0:
                        text = img.get_attribute("alt")
                
                # Ultimo recurso: sacar del URL
                if not text and href:
                    # Ejemplo: .../2024/01/cerro-porteno-vs-trinidense.html
                    parts = href.split("/")
                    last_part = parts[-1].replace(".html", "").replace(".php", "")
                    text = last_part.replace("-", " ").title()

                if href and "goatfutbol.online" in href and ("vs" in href.lower() or "vs" in str(text).lower() or "partido" in str(text).lower()):
                    if href not in unique_links:
                        unique_links.add(href)
                        print(f"Analizando posible partido: {text} ({href})")
                        
                        # Guardar contexto de la pagina principal? No, abrimos nueva logica
                        # O simplemente navegamos y volvemos? Navegar y volver es lento.
                        
                        # Mejor abrir una nueva pagina (tab) para cada partido para no perder la home
                        match_page = browser.new_page()
                        try:
                            match_page.goto(href, timeout=30000)
                            match_page.wait_for_load_state("domcontentloaded")
                            
                            # Buscar iframe correcto
                            player_iframes = match_page.locator("iframe").all()
                            found_url = ""
                            
                            for frame in player_iframes:
                                src = frame.get_attribute("src")
                                if src and src.startswith("https://futbolparaguayotv.github.io/futbolparaguayotv/"):
                                    found_url = src
                                    break
                            
                            if found_url:
                                print(f"  -> Player encontrado: {found_url}")
                                matches_found.append({
                                    "title": text if text else "Partido sin titulo",
                                    "url": found_url
                                })
                            else:
                                print("  -> No se encontro player valido.")
                                
                        except Exception as e_match:
                            print(f"  -> Error procesando partido: {e_match}")
                        finally:
                            match_page.close()

            data["matches"] = matches_found
            if matches_found:
                data["title"] = f"{len(matches_found)} partidos encontrados"
                data["url"] = matches_found[0]["url"] # Fallback para apps viejas
            else:
                data["error"] = "No matches found with valid players"

    except Exception as e:
        print(f"Error: {e}")
        data["error"] = str(e)

    # 5. Guardar JSON
    with open("data.json", "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    
    print("Scraping finalizado.")
    print(json.dumps(data, indent=2, ensure_ascii=False))

if __name__ == "__main__":
    scrape_match()
