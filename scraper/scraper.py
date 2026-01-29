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

            # 2. Buscar todos los contenedores de posts
            print("Buscando partidos...")
            
            matches_found = []
            
            # Mapeo de meses en español
            meses = {
                1: "enero", 2: "febrero", 3: "marzo", 4: "abril",
                5: "mayo", 6: "junio", 7: "julio", 8: "agosto",
                9: "septiembre", 10: "octubre", 11: "noviembre", 12: "diciembre"
            }
            
            now = datetime.now()
            today_str = f"{meses[now.month]} {now.day}, {now.year}"
            print(f"Filtrando por fecha: {today_str}")
            
            # Buscamos los contenedores de cada post
            posts = page.locator("div.post.hentry").all()
            print(f"Post contenedores encontrados: {len(posts)}")

            unique_links = set()
            
            for post in posts:
                # Extraer fecha del post
                date_elem = post.locator(".date-header-post abbr.published").first
                post_date = date_elem.inner_text().strip().lower() if date_elem.count() > 0 else ""
                
                if today_str.lower() not in post_date:
                    continue
                
                # Extraer link y titulo
                link_elem = post.locator("h2.post-title a").first
                if link_elem.count() == 0:
                    continue
                    
                href = link_elem.get_attribute("href")
                text = link_elem.inner_text().strip()
                
                # Si el texto es pobre, limpiar del slug
                if href:
                    parts = href.split("/")
                    slug = parts[-1].replace(".html", "").replace(".php", "")
                    slug = re.sub(r'^\d+-', '', slug)
                    slug = slug.replace("-en-vivo", "")
                    clean_slug_title = slug.replace("-", " ").strip().title()
                    
                    if not text or len(text) < 5 or text == "0":
                        text = clean_slug_title

                if href and "goatfutbol.online" in href and ("vs" in href.lower() or "vs" in str(text).lower()):
                    if href not in unique_links:
                        unique_links.add(href)
                        print(f"Procesando partido: {text} ({href})")
                        
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
                                    "title": text,
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
