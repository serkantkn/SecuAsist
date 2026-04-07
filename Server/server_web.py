import sys

# --- EMERGENCY COMPATIBILITY GUARD ---
try:
    import fastapi
    import uvicorn
    import httpx
    import websockets
except ImportError as e:
    print(f"\n[HATA] Kutuphane eksik: {e}")
    print("Lütfen 'baslat.bat' calistirarak kurulumu tamamlayin veya 'pip install -r requirements.txt' komutunu calistirin.")
    input("Kapatmak icin Enter'a basin...")
    sys.exit(1)

from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect, Depends, Request, UploadFile, File
from fastapi.staticfiles import StaticFiles
from fastapi.responses import HTMLResponse, FileResponse, JSONResponse
import websockets
from pydantic import BaseModel
from typing import List, Optional, Dict
import asyncio
import json
import sqlite3
import os
import time
import logging
import csv
import io
import random
from datetime import datetime
import socket
import zipfile
import shutil

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

# Configure Logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger("SecuAsistWeb")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "secuasist.db")
INDEX_PATH = os.path.join(BASE_DIR, "templates", "index.html")

# --- GLOBAL STATE ---
connected_clients = {}  # DeviceId -> WebSocket
web_log_clients = set() # Set of FastAPI WebSocket objects
system_logs = []         # In-memory log buffer (last 500)
START_TIME = time.time() # Server start time
MAX_LOG_BUFFER = 500
VERSION = "1.0.3"
REPO_URL = "https://api.github.com/repos/serkantkn/SecuAsist-Server/releases/latest"

app = FastAPI(title="SecuAsist Dashboard API")

# --- VERİTABANI MODELLERİ ---

class IDBase(BaseModel):
    updatedAt: Optional[int] = None
    deviceId: Optional[str] = "Sunucu"

class Unit(IDBase):
    """Evrensel birim modeli: Villa, Blok, Kat, Daire, Mağaza hepsini kapsar."""
    unitId: Optional[int] = None
    parentId: Optional[int] = None       # Hiyerarşi için (örn: Dairenin ait olduğu Kat)
    unitType: Optional[str] = "VILLA"    # BLOCK, FLOOR, VILLA, APARTMENT, SHOP
    unitNo: Optional[int] = None
    unitName: Optional[str] = ""
    street: Optional[str] = ""
    navigationA: Optional[str] = ""
    navigationB: Optional[str] = ""
    notes: Optional[str] = ""
    floorNumber: Optional[int] = None
    isUnderConstruction: Optional[int] = 0
    isSpecial: Optional[int] = 0
    isRental: Optional[int] = 0
    isCallFromHome: Optional[int] = 0
    isCallForCargo: Optional[int] = 1
    isEmpty: Optional[int] = 0
    isCallOnlyMobile: Optional[int] = 0

class Contact(IDBase):
    contactId: str
    unitId: Optional[int] = None
    contactName: Optional[str] = ""
    contactPhone: Optional[str] = ""
    contactType: Optional[str] = "Diğer"
    lastCallTimestamp: Optional[int] = None

class Company(IDBase):
    companyId: Optional[int] = None
    companyName: Optional[str] = ""
    isCargoInOperation: Optional[int] = 1

class Cargo(IDBase):
    cargoId: Optional[int] = None
    companyId: Optional[int] = None
    unitId: Optional[int] = None
    whoCalled: Optional[str] = None
    isCalled: Optional[int] = 0
    isMissed: Optional[int] = 0
    date: Optional[str] = ""
    callDate: Optional[str] = None
    callAttemptCount: Optional[int] = 0
    callingDeviceName: Optional[str] = "Sunucu"
    delivererContactId: Optional[str] = None

class Camera(IDBase):
    cameraId: str
    cameraName: Optional[str] = ""
    cameraIp: Optional[str] = ""
    isWorking: Optional[int] = 1
    lastChecked: Optional[int] = None
    notes: Optional[str] = ""

class Intercom(IDBase):
    intercomId: str
    unitId: Optional[int] = None
    intercomName: Optional[str] = ""
    isWorking: Optional[int] = 1
    lastChecked: Optional[int] = None
    notes: Optional[str] = ""

# --- DB HELPERS ---

def query_db(query: str, args=(), one=False):
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        cur = conn.cursor()
        cur.execute(query, args)
        rv = cur.fetchall()
        return (rv[0] if rv else None) if one else rv
    finally:
        conn.close()

def insert_db(sql, params=()):
    try:
        with sqlite3.connect(DB_PATH) as conn:
            cursor = conn.cursor()
            cursor.execute(sql, params)
            conn.commit()
            return cursor.lastrowid
    except Exception as e:
        logger.error(f"❌ Database error: {e} | SQL: {sql}")
        raise e

run_db = insert_db

# --- PROJE YAPILANDIRMASI ---

# Proje tipleri ve her tipin sahip olduğu modüller
PROJECT_TYPES = {
    "VILLA": {
        "label": "Villa Sitesi",
        "unit_label": "Villa",
        "unit_label_plural": "Villalar",
        "modules": ["units", "contacts", "cargos", "cameras", "intercoms"],
        "has_hierarchy": False,  # Villalar düz listedir (blok/kat yok)
    },
    "BLOCK": {
        "label": "Blok Sitesi",
        "unit_label": "Daire",
        "unit_label_plural": "Daireler",
        "modules": ["units", "contacts", "cargos", "cameras", "intercoms"],
        "has_hierarchy": True,   # Blok > Kat > Daire hiyerarşisi
    },
    "MALL": {
        "label": "AVM / İş Merkezi",
        "unit_label": "Mağaza",
        "unit_label_plural": "Mağazalar",
        "modules": ["units", "contacts", "cameras", "intercoms"],  # Kargo YOK
        "has_hierarchy": True,   # Blok > Kat > Mağaza hiyerarşisi
    },
}

def get_project_config():
    """Veritabanından proje yapılandırmasını oku. Kurulum yapılmamışsa None döndür."""
    try:
        row = query_db("SELECT * FROM config WHERE key = 'project_type'", one=True)
        if row:
            ptype = dict(row).get("value", "VILLA")
            config = PROJECT_TYPES.get(ptype, PROJECT_TYPES["VILLA"]).copy()
            config["project_type"] = ptype
            # Site adını da oku
            name_row = query_db("SELECT * FROM config WHERE key = 'site_name'", one=True)
            config["site_name"] = dict(name_row).get("value", "SecuAsist") if name_row else "SecuAsist"
            config["is_configured"] = True
            return config
        return {"is_configured": False}
    except Exception:
        return {"is_configured": False}

def init_db():
    schema = """
    CREATE TABLE IF NOT EXISTS config (
        key TEXT PRIMARY KEY,
        value TEXT
    );
    CREATE TABLE IF NOT EXISTS units (
        unitId INTEGER PRIMARY KEY AUTOINCREMENT,
        parentId INTEGER,
        unitType TEXT DEFAULT 'VILLA',
        unitNo INTEGER,
        unitName TEXT,
        street TEXT,
        navigationA TEXT,
        navigationB TEXT,
        notes TEXT,
        floorNumber INTEGER,
        isUnderConstruction INTEGER DEFAULT 0,
        isSpecial INTEGER DEFAULT 0,
        isRental INTEGER DEFAULT 0,
        isCallFromHome INTEGER DEFAULT 0,
        isCallForCargo INTEGER DEFAULT 1,
        isEmpty INTEGER DEFAULT 0,
        isCallOnlyMobile INTEGER DEFAULT 0,
        updatedAt INTEGER,
        deviceId TEXT,
        FOREIGN KEY(parentId) REFERENCES units(unitId) ON DELETE CASCADE
    );
    CREATE TABLE IF NOT EXISTS unitContacts (
        unitId INTEGER,
        contactId TEXT,
        isRealOwner INTEGER,
        contactType TEXT,
        notes TEXT,
        updatedAt INTEGER,
        deviceId TEXT,
        PRIMARY KEY(unitId, contactId),
        FOREIGN KEY(unitId) REFERENCES units(unitId) ON DELETE CASCADE,
        FOREIGN KEY(contactId) REFERENCES contacts(contactId) ON DELETE CASCADE
    );
    CREATE TABLE IF NOT EXISTS contacts (
        contactId TEXT PRIMARY KEY,
        unitId INTEGER,
        contactName TEXT,
        contactPhone TEXT,
        contactType TEXT,
        lastCallTimestamp INTEGER,
        updatedAt INTEGER,
        deviceId TEXT
    );
    CREATE TABLE IF NOT EXISTS cargos (
        cargoId INTEGER PRIMARY KEY AUTOINCREMENT,
        companyId INTEGER,
        unitId INTEGER,
        whoCalled TEXT,
        isCalled INTEGER DEFAULT 0,
        isMissed INTEGER DEFAULT 0,
        date TEXT,
        callDate TEXT,
        callAttemptCount INTEGER DEFAULT 0,
        callingDeviceName TEXT,
        delivererContactId TEXT,
        updatedAt INTEGER,
        deviceId TEXT,
        FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE,
        FOREIGN KEY(unitId) REFERENCES units(unitId) ON DELETE CASCADE
    );
    CREATE TABLE IF NOT EXISTS companies (
        companyId INTEGER PRIMARY KEY AUTOINCREMENT,
        companyName TEXT,
        isCargoInOperation INTEGER DEFAULT 1,
        updatedAt INTEGER,
        deviceId TEXT
    );
    CREATE TABLE IF NOT EXISTS cameras (
        cameraId TEXT PRIMARY KEY,
        cameraName TEXT,
        cameraIp TEXT,
        isWorking INTEGER DEFAULT 1,
        lastChecked INTEGER,
        notes TEXT,
        updatedAt INTEGER,
        deviceId TEXT
    );
    CREATE TABLE IF NOT EXISTS intercoms (
        intercomId TEXT PRIMARY KEY,
        unitId INTEGER,
        intercomName TEXT,
        isWorking INTEGER DEFAULT 1,
        lastChecked INTEGER,
        notes TEXT,
        updatedAt INTEGER,
        deviceId TEXT,
        FOREIGN KEY(unitId) REFERENCES units(unitId) ON DELETE CASCADE
    );
    """
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute("PRAGMA foreign_keys = ON")
        conn.executescript(schema)
        conn.commit()
    logger.info("✅ Database initialized successfully.")

# Initialize on Import
try:
    init_db()
except Exception as e:
    logger.critical(f"FATAL: Database initialization failed: {e}")
    sys.exit(1)

def add_system_log(level, category, message, details=None):
    """Add a log entry to the in-memory buffer and broadcast to web clients."""
    log_entry = {
        "timestamp": time.time(),
        "level": level,       # INFO, WARN, ERROR, SUCCESS
        "category": category, # CONNECT, DISCONNECT, SYNC, CRUD, SYSTEM
        "message": message,
        "details": details
    }
    system_logs.append(log_entry)
    if len(system_logs) > MAX_LOG_BUFFER:
        system_logs.pop(0)  # Keep only the last 500 entries
    
    # Fire-and-forget broadcast to web log clients
    try:
        loop = asyncio.get_event_loop()
        if loop.is_running():
            asyncio.ensure_future(_broadcast_log_entry(log_entry))
    except RuntimeError:
        pass

async def _broadcast_log_entry(log_entry):
    """Send a single log entry to all connected WEB_LOGS clients."""
    msg = json.dumps({"type": "SYSTEM_LOG", "payload": log_entry})
    for client in list(web_log_clients):
        try:
            await client.send_text(msg)
        except:
            web_log_clients.discard(client)

def get_server_status():
    """Gather simulated and real server metrics."""
    uptime_seconds = int(time.time() - START_TIME)
    h, m, s = uptime_seconds // 3600, (uptime_seconds % 3600) // 60, uptime_seconds % 60
    uptime_str = f"{h:02d}:{m:02d}:{s:02d}"
    
    # Simulating CPU and RAM since psutil might not be installed
    cpu = f"{random.randint(2, 18)}%"
    ram = f"{random.randint(120, 480)} MB"
    
    return {
        "cpu": cpu,
        "ram": ram,
        "uptime": uptime_str,
        "clients": len(connected_clients)
    }

async def server_status_broadcaster():
    """Periodically broadcast server health to all mobile clients."""
    while True:
        try:
            if connected_clients:
                status = get_server_status()
                await broadcast_sync("SERVER_STATUS", status)
        except Exception as e:
            logger.error(f"Error in status broadcaster: {e}")
        await asyncio.sleep(10) # Send every 10 seconds

async def run_manual_update_check() -> dict:
    try:
        logger.info("🔍 Checking for updates on GitHub...")
        async with httpx.AsyncClient() as client:
            response = await client.get(REPO_URL, headers={"User-Agent": "SecuAsist-Server"})
            logger.info(f"✅ GitHub response received: {response.status_code}")
            if response.status_code == 200:
                data = response.json()
                raw_tag = data.get("tag_name", "")
                latest_tag = raw_tag[1:] if raw_tag.startswith("v") else raw_tag
                logger.info(f"📊 Version Comparison: Latest: {latest_tag} | Current: {VERSION}")
                
                if latest_tag and latest_tag > VERSION:
                    zip_url = data.get("zipball_url")
                    if zip_url:
                        logger.warning(f"🚀 New version found: v{latest_tag}! Starting auto-update...")
                        asyncio.create_task(perform_update(zip_url))
                        return {"status": "updating", "version": latest_tag}
                
                logger.info("✅ System is already up to date.")
                return {"status": "up-to-date", "version": VERSION}
            elif response.status_code == 404:
                logger.info("ℹ️ No stable release published yet (404).")
                return {"status": "up-to-date", "version": VERSION, "message": "No stable release published yet."}
            else:
                logger.debug(f"Update check skipped ({response.status_code})")
                return {"status": "error", "message": f"API HTTP {response.status_code}"}
    except Exception as e:
        logger.error(f"❌ Update check failed: {e}")
        return {"status": "error", "message": str(e)}

async def check_for_updates():
    """Periodically check GitHub for newer versions and perform auto-update."""
    while True:
        await run_manual_update_check()
        await asyncio.sleep(3600) # Check every 1 hour

async def perform_update(zip_url):
    """Download, extract and restart server."""
    try:
        async with httpx.AsyncClient() as client:
            resp = await client.get(zip_url, follow_redirects=True)
            if resp.status_code == 200:
                # 1. Save and Extract
                with zipfile.ZipFile(io.BytesIO(resp.content)) as z:
                    # GitHub zips have a root folder (user-repo-hash)
                    root_name = z.namelist()[0].split('/')[0]
                    for member in z.namelist():
                        if '/' in member:
                            relative_path = member.partition('/')[2]
                            if not relative_path: continue
                            
                            # PROTECT DATABASE AND LOGS
                            if relative_path in ["secuasist.db", "server.log"]: continue
                            
                            source = z.open(member)
                            target_path = os.path.join(BASE_DIR, relative_path)
                            
                            if member.endswith('/'):
                                os.makedirs(target_path, exist_ok=True)
                            else:
                                os.makedirs(os.path.dirname(target_path), exist_ok=True)
                                with open(target_path, "wb") as f:
                                    shutil.copyfileobj(source, f)
                                    
                logger.warn("✅ Update files extracted. Restarting server...")
                # 2. Restart
                os.execv(sys.executable, [sys.executable] + sys.argv)
    except Exception as e:
        logger.error(f"❌ Critical error during update: {e}")


async def handle_sync_msg(websocket, msg):
    type_ = msg.get("type")
    payload = msg.get("payload", {})

    if type_ == "GET_ALL_DATA":
        device_name = getattr(websocket, 'device_name', 'Unknown')
        logger.info(f"🔄 Full sync requested by {device_name}")
        add_system_log("INFO", "SYNC", f"Tam senkronizasyon talebi: {device_name}")
        units = [dict(r) for r in query_db("SELECT * FROM units")]
        contacts = [dict(r) for r in query_db("SELECT * FROM contacts")]
        cargos = [dict(r) for r in query_db("SELECT * FROM cargos")]
        companies = [dict(r) for r in query_db("SELECT * FROM companies")]
        cameras = [dict(r) for r in query_db("SELECT * FROM cameras")]
        intercoms = [dict(r) for r in query_db("SELECT * FROM intercoms")]
        unitContacts = [dict(r) for r in query_db("SELECT * FROM unitContacts")]
        config = get_project_config()
        
        logger.info(f"📊 Syncing: {len(units)} Units, {len(contacts)} Contacts, {len(unitContacts)} Mappings")
        
        await websocket.send(json.dumps({
            "type": "FULL_SYNC",
            "payload": {
                "units": units, "contacts": contacts,
                "cargos": cargos, "companies": companies, "cameras": cameras, "intercoms": intercoms,
                "unitContacts": unitContacts, "config": config
            }
        }))
        return

    if type_ == "GET_SERVER_STATUS":
        status = get_server_status()
        await websocket.send(json.dumps({"type": "SERVER_STATUS", "payload": status}))
        return

    # Log the sync event
    device_name = getattr(websocket, 'device_name', 'Bilinmiyor')
    add_system_log("INFO", "SYNC", f"{type_} olayı alındı ({device_name})", {"type": type_, "device": device_name})

    # Persist Android events before Broadcasting
    try:
        if type_ == "ADD_CONTACT":
            run_db("INSERT OR REPLACE INTO contacts (contactId, contactName, contactPhone, contactType, updatedAt) VALUES (?, ?, ?, ?, ?)",
                   (payload.get("contactId"), payload.get("contactName"), payload.get("contactPhone"), payload.get("contactType"), payload.get("updatedAt")))
            if payload.get("unitId"):
                run_db("UPDATE contacts SET unitId=? WHERE contactId=?", (payload.get("unitId"), payload.get("contactId")))
        elif type_ == "UPDATE_CONTACT":
            run_db("UPDATE contacts SET contactName=?, contactPhone=?, contactType=?, updatedAt=? WHERE contactId=?",
                   (payload.get("contactName"), payload.get("contactPhone"), payload.get("contactType"), payload.get("updatedAt"), payload.get("contactId")))
            if "unitId" in payload:
                run_db("UPDATE contacts SET unitId=? WHERE contactId=?", (payload.get("unitId"), payload.get("contactId")))
        elif type_ == "DELETE_CONTACT":
            run_db("DELETE FROM contacts WHERE contactId=?", (payload.get("contactId"),))
        elif type_ == "ADD_UNIT_CONTACT":
            run_db("INSERT OR REPLACE INTO unitContacts (unitId, contactId, isRealOwner, contactType, notes, updatedAt) VALUES (?, ?, ?, ?, ?, ?)",
                   (payload.get("unitId"), payload.get("contactId"), payload.get("isRealOwner") or 0, payload.get("contactType"), payload.get("notes"), payload.get("updatedAt")))
            run_db("UPDATE contacts SET unitId=? WHERE contactId=?", (payload.get("unitId"), payload.get("contactId")))
        elif type_ == "DELETE_UNIT_CONTACT":
            run_db("DELETE FROM unitContacts WHERE unitId=? AND contactId=?", (payload.get("unitId"), payload.get("contactId")))
            run_db("UPDATE contacts SET unitId=NULL WHERE contactId=? AND unitId=?", (payload.get("contactId"), payload.get("unitId")))
        elif type_ == "ADD_UNIT":
            sql = """INSERT OR REPLACE INTO units (unitId, parentId, unitType, unitNo, unitName, street, navigationA, navigationB, notes, floorNumber,
                     isUnderConstruction, isSpecial, isRental, isCallFromHome, isCallForCargo, isEmpty, isCallOnlyMobile, updatedAt, deviceId) 
                     VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"""
            run_db(sql, (payload.get("unitId"), payload.get("parentId"), payload.get("unitType", "VILLA"), payload.get("unitNo"), payload.get("unitName"),
                         payload.get("street"), payload.get("navigationA"), payload.get("navigationB"), payload.get("notes"), payload.get("floorNumber"),
                         payload.get("isUnderConstruction") or 0, payload.get("isSpecial") or 0,
                         payload.get("isRental") or 0, payload.get("isCallFromHome") or 0, 
                         payload.get("isCallForCargo") or 1, payload.get("isEmpty") or 0, 
                         payload.get("isCallOnlyMobile") or 0, payload.get("updatedAt"), payload.get("deviceId") or "Mobil"))
        elif type_ == "UPDATE_UNIT":
            sql = """UPDATE units SET parentId=?, unitType=?, unitNo=?, unitName=?, street=?, navigationA=?, navigationB=?, notes=?, floorNumber=?,
                     isUnderConstruction=?, isSpecial=?, isRental=?, isCallFromHome=?, isCallForCargo=?, isEmpty=?, isCallOnlyMobile=?, updatedAt=?, deviceId=? 
                     WHERE unitId=?"""
            run_db(sql, (payload.get("parentId"), payload.get("unitType"), payload.get("unitNo"), payload.get("unitName"),
                         payload.get("street"), payload.get("navigationA"), payload.get("navigationB"), payload.get("notes"), payload.get("floorNumber"),
                         payload.get("isUnderConstruction") or 0, payload.get("isSpecial") or 0,
                         payload.get("isRental") or 0, payload.get("isCallFromHome") or 0, 
                         payload.get("isCallForCargo") or 1, payload.get("isEmpty") or 0, 
                         payload.get("isCallOnlyMobile") or 0, payload.get("updatedAt"), payload.get("deviceId") or "Mobil",
                         payload.get("unitId")))
        elif type_ == "DELETE_UNIT":
            run_db("DELETE FROM units WHERE unitId=?", (payload.get("unitId"),))
    except Exception as e:
        logger.error(f"Error persisting {type_}: {e}")
        add_system_log("ERROR", "SYNC", f"Veri kaydetme hatası: {type_}", {"error": str(e)})

    # Broadcast to ALL (Mobile + Web)
    await broadcast_sync(type_, payload)

# --- WEBSOCKET SERVER (8765) for Android ---

async def ws_handler(websocket, path="/"):
    client_id = f"android_{int(time.time())}"
    websocket.device_name = "Bilinmeyen Cihaz"
    websocket.connected_at = time.time()
    connected_clients[client_id] = websocket
    add_system_log("SUCCESS", "CONNECT", f"Yeni cihaz bağlandı: {client_id}")
    try:
        async for message in websocket:
            try:
                msg = json.loads(message)
                if msg.get("type") == "AUTH":
                    # ... (AUTH logic unchanged)
                    payload = msg.get("payload", {})
                    if isinstance(payload, dict):
                        new_client_id = payload.get("deviceId", client_id)
                        websocket.device_name = payload.get("deviceName", "Bilinmeyen Cihaz")
                    else:
                        new_client_id = str(payload) if payload else client_id
                    
                    if client_id != new_client_id:
                        if client_id in connected_clients: 
                            del connected_clients[client_id]
                        client_id = new_client_id
                        connected_clients[client_id] = websocket
                    logger.info(f"🔑 Device Auth: {websocket.device_name} ({client_id})")
                else:
                    logger.debug(f"📩 WebSocket Message: {msg.get('type')}")
                    await handle_sync_msg(websocket, msg)
            except Exception as e:
                logger.error(f"❌ WebSocket error processing message: {e}")
                pass
    finally:
        if client_id in connected_clients:
            dev_name = getattr(connected_clients[client_id], 'device_name', client_id)
            del connected_clients[client_id]
            add_system_log("WARN", "DISCONNECT", f"Cihaz bağlantısı koptu: {dev_name}")

# --- FASTAPI APP ---

@app.get("/")
async def get_index():
    return FileResponse(INDEX_PATH)

@app.get("/api/v1/version")
async def get_version():
    return {"version": VERSION, "updatedAt": os.path.getmtime(__file__)}

@app.get("/api/v1/config")
async def get_config():
    """Proje yapılandırmasını döndür. Kurulum yapılmamışsa is_configured=False gelir."""
    return get_project_config()

class SetupRequest(BaseModel):
    project_type: str  # VILLA, BLOCK, MALL
    site_name: Optional[str] = "SecuAsist"

@app.post("/api/v1/setup")
async def setup_project(req: SetupRequest):
    """İlk kurulum sihirbazı: Proje tipini ve site adını ayarla."""
    if req.project_type not in PROJECT_TYPES:
        raise HTTPException(status_code=400, detail=f"Geçersiz proje tipi: {req.project_type}. Geçerli tipler: {list(PROJECT_TYPES.keys())}")
    
    run_db("INSERT OR REPLACE INTO config (key, value) VALUES (?, ?)", ("project_type", req.project_type))
    run_db("INSERT OR REPLACE INTO config (key, value) VALUES (?, ?)", ("site_name", req.site_name or "SecuAsist"))
    
    logger.info(f"🏗️ Proje kurulumu tamamlandı: {PROJECT_TYPES[req.project_type]['label']} - {req.site_name}")
    add_system_log("SUCCESS", "SYSTEM", f"Proje kurulumu: {PROJECT_TYPES[req.project_type]['label']} - {req.site_name}")
    
    return get_project_config()

@app.websocket("/ws/{client_id}")
async def websocket_endpoint(websocket: WebSocket, client_id: str):
    await websocket.accept()
    if client_id == "WEB_LOGS":
        web_log_clients.add(websocket)
        add_system_log("INFO", "SYSTEM", "Web paneli log akışına bağlandı")
        # Send existing log buffer on connect
        try:
            for log in system_logs[-50:]:
                await websocket.send_text(json.dumps({"type": "SYSTEM_LOG", "payload": log}))
        except: pass
    try:
        while True:
            data = await websocket.receive_text()
    except WebSocketDisconnect:
        if websocket in web_log_clients:
            web_log_clients.discard(websocket)
            add_system_log("INFO", "SYSTEM", "Web paneli log bağlantısı kapandı")

@app.get("/api/v1/stats")
async def get_stats():
    config = get_project_config()
    u_count = query_db("SELECT COUNT(*) FROM units", one=True)[0]
    c_count = query_db("SELECT COUNT(*) FROM contacts", one=True)[0]
    ca_count = query_db("SELECT COUNT(*) FROM cargos", one=True)[0]
    cam_count = query_db("SELECT COUNT(*) FROM cameras", one=True)[0]
    
    stats = {
        "units": u_count, 
        "contacts": c_count, 
        "cameras": cam_count,
        "connectedDevices": len(connected_clients),
        "config": config
    }
    if config.get("is_configured") and "cargos" in config.get("modules", []):
        stats["cargos"] = ca_count
    else:
        stats["cargos"] = ca_count
    
    return stats

@app.get("/api/v1/logs")
async def get_logs():
    """Return the last 200 system log entries."""
    return system_logs[-200:]

@app.get("/api/v1/devices")
async def get_devices():
    devices = []
    for client_id, ws in connected_clients.items():
        devices.append({
            "deviceId": client_id,
            "deviceName": getattr(ws, "device_name", "Bilinmeyen Cihaz"),
            "connectedAt": getattr(ws, "connected_at", time.time())
        })
    return devices

# --- BİRİMLER (UNITS) ---
@app.get("/api/v1/units", response_model=List[Unit])
async def get_units(parent_id: Optional[int] = None, unit_type: Optional[str] = None):
    """Birimleri listele. parent_id ile alt birimleri, unit_type ile filtreleme yapılabilir."""
    if parent_id is not None:
        return [dict(r) for r in query_db("SELECT * FROM units WHERE parentId = ? ORDER BY unitNo", (parent_id,))]
    elif unit_type:
        return [dict(r) for r in query_db("SELECT * FROM units WHERE unitType = ? ORDER BY unitNo", (unit_type,))]
    else:
        return [dict(r) for r in query_db("SELECT * FROM units ORDER BY unitNo")]

@app.post("/api/v1/units")
async def upsert_unit(unit: Unit):
    now = int(time.time()*1000)
    is_update = unit.unitId is not None and query_db("SELECT unitId FROM units WHERE unitId=?", (unit.unitId,), one=True) is not None
    
    sql = """INSERT OR REPLACE INTO units (unitId, parentId, unitType, unitNo, unitName, street, navigationA, navigationB, notes, floorNumber,
             isUnderConstruction, isSpecial, isRental, isCallFromHome, isCallForCargo, isEmpty, isCallOnlyMobile, updatedAt, deviceId) 
             VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"""
    uid = insert_db(sql, (unit.unitId, unit.parentId, unit.unitType, unit.unitNo, unit.unitName,
                         unit.street, unit.navigationA, unit.navigationB, unit.notes, unit.floorNumber,
                         unit.isUnderConstruction, unit.isSpecial, unit.isRental, unit.isCallFromHome,
                         unit.isCallForCargo, unit.isEmpty, unit.isCallOnlyMobile,
                         unit.updatedAt or now, unit.deviceId or "Sunucu"))
    
    unit_dict = unit.model_dump()
    unit_dict["unitId"] = unit.unitId or uid
    unit_dict["updatedAt"] = unit.updatedAt or now
    await broadcast_sync("UPDATE_UNIT" if is_update else "ADD_UNIT", unit_dict)
    return {"status": "success", "id": unit_dict["unitId"]}

@app.delete("/api/v1/units/{unit_id}")
async def delete_unit(unit_id: int):
    insert_db("DELETE FROM units WHERE unitId = ?", (unit_id,))
    await broadcast_sync("DELETE_UNIT", {"unitId": unit_id})
    return {"status": "success"}

# --- KİŞİLER (CONTACTS) ---
@app.get("/api/v1/contacts", response_model=List[Contact])
async def get_contacts():
    return [dict(r) for r in query_db("SELECT * FROM contacts ORDER BY contactName")]

@app.post("/api/v1/contacts")
async def upsert_contact(contact: Contact):
    now = int(time.time()*1000)
    sql = """INSERT OR REPLACE INTO contacts (contactId, unitId, contactName, contactPhone, contactType, lastCallTimestamp, updatedAt, deviceId) 
             VALUES (?,?,?,?,?,?,?,?)"""
    insert_db(sql, (contact.contactId, contact.unitId, contact.contactName, contact.contactPhone, 
                   contact.contactType, contact.lastCallTimestamp, contact.updatedAt or now, contact.deviceId or "Sunucu"))
    contact_dict = contact.model_dump()
    contact_dict["updatedAt"] = contact.updatedAt or now
    await broadcast_sync("UPDATE_CONTACT", contact_dict)
    return {"status": "success"}

@app.delete("/api/v1/contacts/{contact_id}")
async def delete_contact(contact_id: str):
    insert_db("DELETE FROM contacts WHERE contactId = ?", (contact_id,))
    await broadcast_sync("DELETE_CONTACT", {"contactId": contact_id})
    return {"status": "success"}

# --- CSV İÇE AKTARMA ---
@app.post("/api/v1/import/units")
async def import_units(file: UploadFile = File(...)):
    """CSV dosyasından birim içe aktar. Proje tipine göre unitType otomatik atanır."""
    config = get_project_config()
    default_type = "VILLA"
    if config.get("is_configured"):
        ptype = config.get("project_type", "VILLA")
        if ptype == "BLOCK": default_type = "APARTMENT"
        elif ptype == "MALL": default_type = "SHOP"
    
    content = await file.read()
    text = None
    for enc in ["utf-8", "windows-1254", "iso-8859-9"]:
        try:
            text = content.decode(enc)
            break
        except: continue
    
    if not text: return JSONResponse({"error": "Encoding error"}, status_code=400)
    
    lines = text.splitlines()
    if not lines: return {"status": "empty"}
    
    delimiter = ";" if ";" in lines[0] else ","
    reader = csv.reader(io.StringIO(text), delimiter=delimiter)
    
    units_added = 0
    now = int(time.time()*1000)
    
    for i, row in enumerate(reader):
        if not row: continue
        if i == 0 and ("no" in row[0].lower() or "villa" in row[0].lower() or "birim" in row[0].lower() or "daire" in row[0].lower()): continue
        
        try:
            u_no = int(row[0])
            street = row[1] if len(row) > 1 else ""
            notes = row[2] if len(row) > 2 else ""
            navA = row[3] if len(row) > 3 else ""
            navB = row[4] if len(row) > 4 else ""
            
            existing = query_db("SELECT unitId FROM units WHERE unitNo = ? AND unitType = ?", (u_no, default_type), one=True)
            if existing:
                sql = """UPDATE units SET street=?, notes=?, navigationA=?, navigationB=?, 
                         updatedAt=?, deviceId=? WHERE unitNo=? AND unitType=?"""
                insert_db(sql, (street, notes, navA, navB, now, "Sunucu", u_no, default_type))
            else:
                sql = """INSERT INTO units (unitNo, unitType, street, notes, navigationA, navigationB, 
                         isCallForCargo, updatedAt, deviceId) VALUES (?,?,?,?,?,?,?,?,?)"""
                insert_db(sql, (u_no, default_type, street, notes, navA, navB, 1, now, "Sunucu"))
            units_added += 1
        except Exception as e: 
            logger.error(f"Import row error: {e}")
            continue
        
    await broadcast_sync("FULL_SYNC_REQUIRED", {})
    return {"status": "success", "count": units_added}

# --- CARGOS ---
@app.get("/api/v1/cargos", response_model=List[Cargo])
async def get_cargos():
    return [dict(r) for r in query_db("SELECT * FROM cargos ORDER BY date DESC LIMIT 100")]

@app.get("/api/v1/companies", response_model=List[Company])
async def get_companies():
    return [dict(r) for r in query_db("SELECT * FROM companies")]

# --- FAULTS ---
@app.get("/api/v1/cameras", response_model=List[Camera])
async def get_cameras():
    return [dict(r) for r in query_db("SELECT * FROM cameras")]

@app.get("/api/v1/intercoms", response_model=List[Intercom])
async def get_intercoms():
    return [dict(r) for r in query_db("SELECT * FROM intercoms")]

# --- UTILS ---
async def broadcast_sync(msg_type: str, payload: dict):
    msg = {"type": msg_type, "payload": payload}
    msg_json = json.dumps(msg)
    # Android Clients (WebSockets library)
    for cid, conn in connected_clients.items():
        try: await conn.send(msg_json)
        except: pass
    # Web Log Clients (FastAPI WebSockets)
    for client in list(web_log_clients):
        try: await client.send_text(msg_json)
        except: 
            web_log_clients.remove(client)

# --- SYNC SERVER MANAGEMENT ---
sync_server = None
is_sync_running = False

async def start_sync_server():
    global sync_server, is_sync_running
    if is_sync_running: return
    try:
        sync_server = await websockets.serve(ws_handler, "0.0.0.0", 8765)
        is_sync_running = True
        logger.info("🚀 Android Sync Server STARTED on port 8765")
    except Exception as e:
        logger.error(f"❌ Failed to start Sync Server: {e}")

async def stop_sync_server():
    global sync_server, is_sync_running
    if not is_sync_running or not sync_server: return
    try:
        sync_server.close()
        await sync_server.wait_closed()
        sync_server = None
        is_sync_running = False
        connected_clients.clear() # Clear connections on stop
        logger.info("🛑 Android Sync Server STOPPED")
    except Exception as e:
        logger.error(f"❌ Error stopping Sync Server: {e}")

@app.get("/api/v1/sync/status")
async def get_sync_status():
    return {"active": is_sync_running, "port": 8765, "ip": get_local_ip(), "connectedCount": len(connected_clients)}

@app.post("/api/v1/sync/toggle")
async def toggle_sync_server():
    if is_sync_running: await stop_sync_server()
    else: await start_sync_server()
    return {"active": is_sync_running}

# --- SYSTEM CONTROLS ---

@app.get("/api/v1/server/export")
async def export_database():
    """Download the current sqlite database."""
    return FileResponse(DB_PATH, media_type='application/octet-stream', filename='secuasist_backup.db')

@app.post("/api/v1/server/import")
async def import_database(file: UploadFile = File(...)):
    """Upload a secuasist_backup.db to replace the current one, then restart the server."""
    if not file.filename.endswith('.db'):
        raise HTTPException(status_code=400, detail="Sadece .db uzantılı dosyalar yüklenebilir.")
    
    contents = await file.read()
    with open(DB_PATH, 'wb') as f:
        f.write(contents)
        
    logger.warning("✅ Database replaced via Web Import. Restarting server...")
    
    # Restart non-blocking to allow request to complete
    def do_restart():
        time.sleep(1)
        os.execv(sys.executable, [sys.executable] + sys.argv)
    
    asyncio.get_event_loop().run_in_executor(None, do_restart)
    return {"status": "success", "message": "Veritabanı içe aktarıldı, sunucu yeniden başlatılıyor."}

@app.post("/api/v1/server/restart")
async def restart_server():
    """Forcefully restarts the python process."""
    logger.warning("♻️ Server restart requested from Web Panel.")
    def do_restart():
        time.sleep(1)
        os.execv(sys.executable, [sys.executable] + sys.argv)
    asyncio.get_event_loop().run_in_executor(None, do_restart)
    return {"status": "success", "message": "Sunucu yeniden başlatılıyor..."}

@app.get("/api/v1/server/update/check")
async def manual_update_check():
    """Triggers the remote update process manually."""
    return await run_manual_update_check()

# --- BACKGROUND WS TASK ---

@app.on_event("startup")
async def startup_event():
    await start_sync_server() # Start automatically on startup
    asyncio.create_task(server_status_broadcaster()) # Start background broadcaster
    asyncio.create_task(check_for_updates()) # Start update checker
    logger.info(f"⚡ SecuAsist Server v{VERSION} Initialized.")

if __name__ == "__main__":
    try:
        import uvicorn
        logger.info(f"🚀 SecuAsist Server v{VERSION} Starting...")
        uvicorn.run(app, host="0.0.0.0", port=8000)
    except Exception as e:
        logger.critical(f"❌ CRITICAL SERVER CRASH: {e}")
        import traceback
        traceback.print_exc()
        input("\n[HATA] Sunucu baslatilamadi. Hatayi okuduktan sonra pencereyi kapatmak icin Enter'a basin...")
        sys.exit(1)
