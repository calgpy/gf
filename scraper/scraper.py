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

            # 2. Buscar el primer post que parezca un partido
            # Buscamos enlaces que tengan formato de fecha o "vs"
            print("Buscando partidos...")
            
            # Estrategia: Obtener el primer enlace dentro de la sección de entradas o noticias
            # Ajustar selector según la estructura real. Basado en analisis previo:
            # Los posts suelen estar en un contenedor main o article.
            
            # Intentamos tomar el primer artículo que no sea "Inicio" o "Agenda"
            # Buscamos un link que contenga "vs" en su texto o URL
            match_link = page.locator("a[href*='vs']").first
            
            if not match_link.count():
                print("No se encontraron enlaces de partidos 'vs'.")
                data["error"] = "No match links found"
            else:
                title = match_link.inner_text().strip()
                url = match_link.get_attribute("href")
                
                print(f"Partido encontrado: {title}")
                print(f"URL: {url}")
                
                data["title"] = title
                
                # 3. Entrar al partido
                page.goto(url, timeout=60000)
                page.wait_for_load_state("domcontentloaded")
                
                # 4. Buscar el iframe final
                print("Buscando reproductor...")
                
                # Buscamos iframes cerca de "OPCION"
                # A veces hay iframes anidados. 
                # Simplificación: Buscar todos los iframes y ver cual es el del reproductor.
                # En el analisis anterior vimos que era 'futbolparaguayotv.github.io'
                
                found_player = False
                iframes = page.locator("iframe").all()
                
                for frame_loc in iframes:
                    src = frame_loc.get_attribute("src")
                    if src and "futbolparaguayotv" in src:
                        data["url"] = src
                        found_player = True
                        break
                
                if not found_player:
                    # Si no es ese dominio especifico, intentamos tomar el iframe que sigue a "OPCION 1"
                    opcion_label = page.get_by_text("OPCION 1").first
                    if opcion_label.count():
                        # Buscar iframe cercano
                        # Esto es tricky con selectores, a veces mejor agarrar todos los srcs
                        pass
                        
                    # Fallback: Guardar el primer iframe que no sea de publicidad (ads)
                    # O simplemente guardar la URL del post para que la App lo procese (pero el user quiere el reproductor)
                    pass

            browser.close()

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
