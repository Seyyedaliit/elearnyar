#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
elearnyar - iOS Web App Local Server & CORS Proxy
سامانه ایلرن! - وب‌اپلیکیشن برای آیفون و مرورگرها
"""

import sys
import os
import json
import ssl
import socket
import urllib.request
import urllib.parse
import http.cookiejar
from http.server import HTTPServer, SimpleHTTPRequestHandler
from socketserver import ThreadingMixIn

BASE_URL = "https://elearn.uk.ac.ir"
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

# Create unverified SSL context for elearn server
ssl_ctx = ssl._create_unverified_context()

class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True

class ElearnProxyHandler(SimpleHTTPRequestHandler):
    def end_headers(self):
        # Enable CORS and caching headers
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With')
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == '/api/download':
            self.handle_download(parsed)
            return
        
        # Serve static web files
        super().do_GET()

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == '/api/courses':
            self.handle_fetch_courses()
            return
        
        self.send_error(404, "Endpoint not found")

    def handle_download(self, parsed):
        query = urllib.parse.parse_qs(parsed.query)
        guid = query.get('guid', [''])[0]
        if not guid:
            self.send_error(400, "Missing guid")
            return
        target_url = f"{BASE_URL}/Downloader.aspx?guid={urllib.parse.quote(guid)}"
        self.send_response(302)
        self.send_header('Location', target_url)
        self.end_headers()

    def handle_fetch_courses(self):
        try:
            content_length = int(self.headers.get('Content-Length', 0))
            post_body = self.rfile.read(content_length)
            req_data = json.loads(post_body.decode('utf-8'))
            username = req_data.get('username', '').strip()
            password = req_data.get('password', '').strip()

            if not username or not password:
                self.send_json_response({"error": "نام کاربری و کلمه عبور الزامی است."}, 400)
                return

            # Call elearn scraper
            courses = fetch_all_elearn_data(username, password)
            self.send_json_response({"status": "ok", "courses": courses}, 200)

        except Exception as e:
            print(f"[!] خطای پردازش: {str(e)}")
            self.send_json_response({"error": str(e)}, 500)

    def send_json_response(self, data, status=200):
        body = json.dumps(data, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def make_opener():
    cj = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(cj),
        urllib.request.HTTPSHandler(context=ssl_ctx)
    )
    return opener


def fetch_all_elearn_data(username, password):
    opener = make_opener()

    # 1. GET /index.aspx
    req = urllib.request.Request(f"{BASE_URL}/index.aspx", headers={"User-Agent": USER_AGENT})
    with opener.open(req, timeout=20) as resp:
        resp.read()

    # 2. POST /Index.aspx/get_login
    login_payload = json.dumps({
        "valuser": username,
        "valpass": password,
        "setpass": "0"
    }).encode('utf-8')

    login_req = urllib.request.Request(
        f"{BASE_URL}/Index.aspx/get_login",
        data=login_payload,
        headers={
            "User-Agent": USER_AGENT,
            "Content-Type": "application/json; charset=utf-8",
            "Accept": "application/json, text/javascript, */*; q=0.01",
            "X-Requested-With": "XMLHttpRequest",
            "Referer": f"{BASE_URL}/index.aspx"
        }
    )

    with opener.open(login_req, timeout=20) as resp:
        login_res = json.loads(resp.read().decode('utf-8'))
        d = login_res.get("d") or {}
        if d.get("Item1") != "ok":
            msg = d.get("Item2", "نام کاربری یا کلمه عبور اشتباه است.")
            raise Exception(msg or "نام کاربری یا کلمه عبور اشتباه است.")

    # 3. GET /STClassList.aspx
    req_class = urllib.request.Request(
        f"{BASE_URL}/STClassList.aspx",
        headers={"User-Agent": USER_AGENT, "Referer": f"{BASE_URL}/index.aspx"}
    )
    with opener.open(req_class, timeout=20) as resp:
        resp.read()

    # 4. GET /STClasslist.aspx/GetSTClasses
    req_courses = urllib.request.Request(
        f"{BASE_URL}/STClasslist.aspx/GetSTClasses",
        headers={
            "User-Agent": USER_AGENT,
            "Content-Type": "application/json; charset=utf-8",
            "Accept": "application/json, text/javascript, */*; q=0.01",
            "X-Requested-With": "XMLHttpRequest",
            "Referer": f"{BASE_URL}/STClassList.aspx"
        }
    )

    with opener.open(req_courses, timeout=25) as resp:
        raw_res = json.loads(resp.read().decode('utf-8'))
        courses_json_str = raw_res.get("d", "[]")
        courses_raw = json.loads(courses_json_str)

    courses = []
    for item in courses_raw:
        c = {
            "tcInfoId": str(item.get("TCInfoID", "")),
            "name": item.get("classname", ""),
            "groupNo": str(item.get("LessonGroupNo", "")),
            "unitType": item.get("unittype", 1),
            "teacherName": item.get("TeacherName", ""),
            "exercisesCount": item.get("Exercisecount", 0),
            "learnCount": item.get("learncount", 0),
            "unreadMessages": item.get("messagecount", 0),
            "exercises": [],
            "learningFiles": [],
            "messages": [],
            "attendanceList": []
        }
        courses.append(c)

    # 5. Fetch details for each course
    for course in courses:
        tc_id = course["tcInfoId"]
        if not tc_id:
            continue

        course_url = f"{BASE_URL}/STCoursepage.aspx?CLid={urllib.parse.quote(tc_id)}"

        try:
            # 5.1 Visit STCoursepage.aspx
            req_cpage = urllib.request.Request(course_url, headers={"User-Agent": USER_AGENT})
            with opener.open(req_cpage, timeout=15) as resp:
                resp.read()

            # 5.2 GetInfo
            req_info = urllib.request.Request(
                f"{BASE_URL}/STCoursepage.aspx/GetInfo",
                data=b"{}",
                headers={
                    "User-Agent": USER_AGENT,
                    "Content-Type": "application/json; charset=utf-8",
                    "X-Requested-With": "XMLHttpRequest",
                    "Referer": course_url
                }
            )
            with opener.open(req_info, timeout=15) as resp:
                info_raw = json.loads(resp.read().decode('utf-8'))
                info_list = json.loads(info_raw.get("d", "[]"))
                if info_list and len(info_list) > 0:
                    first = info_list[0]
                    if first.get("teachername"):
                        course["teacherName"] = first.get("teachername")
                    course["examDate"] = first.get("ExamPersianDate", "")
                    course["examTime"] = first.get("ExamStartTime", "")
        except Exception:
            pass

        # 5.3 ExerciseFileList
        try:
            req_ex = urllib.request.Request(
                f"{BASE_URL}/STCoursepage.aspx/ExerciseFileList",
                data=json.dumps({"startpage": "0", "rowcount": "50"}).encode('utf-8'),
                headers={
                    "User-Agent": USER_AGENT,
                    "Content-Type": "application/json; charset=utf-8",
                    "X-Requested-With": "XMLHttpRequest",
                    "Referer": course_url
                }
            )
            with opener.open(req_ex, timeout=15) as resp:
                ex_raw = json.loads(resp.read().decode('utf-8'))
                ex_data = json.loads(ex_raw.get("d", "{}"))
                aa_data = ex_data.get("aaData") or []
                for row in aa_data:
                    course["exercises"].append({
                        "exid": str(row.get("exid", "")),
                        "fid": row.get("fid", 0),
                        "title": row.get("extitle", ""),
                        "description": row.get("exDescription", ""),
                        "startDate": row.get("StartDate", ""),
                        "endDate": row.get("EndDate", ""),
                        "grade": row.get("Grade", 0.0),
                        "exGuid": row.get("ExGuid", ""),
                        "fGuid": row.get("FGuid", ""),
                        "filePath": row.get("lfilepath", ""),
                        "courseName": course["name"],
                        "tcInfoId": tc_id
                    })
        except Exception:
            pass

        # 5.4 LearningFileList
        try:
            req_lf = urllib.request.Request(
                f"{BASE_URL}/STCoursepage.aspx/LearningFileList",
                data=json.dumps({"startpage": "0", "rowcount": "50"}).encode('utf-8'),
                headers={
                    "User-Agent": USER_AGENT,
                    "Content-Type": "application/json; charset=utf-8",
                    "X-Requested-With": "XMLHttpRequest",
                    "Referer": course_url
                }
            )
            with opener.open(req_lf, timeout=15) as resp:
                lf_raw = json.loads(resp.read().decode('utf-8'))
                lf_data = json.loads(lf_raw.get("d", "{}"))
                aa_data = lf_data.get("aaData") or []
                for row in aa_data:
                    course["learningFiles"].append({
                        "lid": str(row.get("lid", "")),
                        "fid": row.get("fid", 0),
                        "title": row.get("ltitle", ""),
                        "description": row.get("lDescription", ""),
                        "insertDate": row.get("insertdate", ""),
                        "fGuid": row.get("FGuid", ""),
                        "leGuid": row.get("LeGuid", ""),
                        "filePath": row.get("lfilepath", ""),
                        "courseName": course["name"],
                        "tcInfoId": tc_id
                    })
        except Exception:
            pass

        # 5.5 Messages
        try:
            msg_url = f"{BASE_URL}/STCoursepage.aspx/GetMessageList?TCInfoID={urllib.parse.quote(tc_id)}"
            req_msg = urllib.request.Request(
                msg_url,
                headers={
                    "User-Agent": USER_AGENT,
                    "Content-Type": "application/json; charset=utf-8",
                    "Accept": "application/json, text/javascript, */*; q=0.01",
                    "X-Requested-With": "XMLHttpRequest",
                    "Referer": course_url
                }
            )
            raw_msg_str = None
            try:
                with opener.open(req_msg, timeout=15) as resp:
                    raw_msg_str = resp.read().decode('utf-8')
            except Exception:
                # Try POST fallback
                req_msg_post = urllib.request.Request(
                    f"{BASE_URL}/STCoursepage.aspx/GetMessageList",
                    data=json.dumps({"TCInfoID": tc_id}).encode('utf-8'),
                    headers={
                        "User-Agent": USER_AGENT,
                        "Content-Type": "application/json; charset=utf-8",
                        "X-Requested-With": "XMLHttpRequest",
                        "Referer": course_url
                    }
                )
                with opener.open(req_msg_post, timeout=15) as resp:
                    raw_msg_str = resp.read().decode('utf-8')

            if raw_msg_str:
                obj = json.loads(raw_msg_str)
                d_str = obj.get("d", "")
                if d_str:
                    m_data = json.loads(d_str)
                    aa_data = m_data.get("aaData") or []
                    for idx, row in enumerate(aa_data):
                        course["messages"].append({
                            "id": str(row.get("Id", row.get("id", idx + 1))),
                            "title": row.get("Title", row.get("title", "")),
                            "senderName": row.get("fullname", row.get("FullName", course["teacherName"])),
                            "insertDate": row.get("InsertDate", row.get("insertdate", "")),
                            "message": row.get("Message", row.get("message", "")),
                            "isRead": (row.get("isread", 1) == 1),
                            "courseName": course["name"],
                            "tcInfoId": tc_id
                        })
        except Exception:
            pass

        # 5.6 Attendance
        try:
            att_url = f"{BASE_URL}/STCoursepage.aspx/GetAttendanceList?TCInfoID={urllib.parse.quote(tc_id)}"
            req_att = urllib.request.Request(
                att_url,
                headers={
                    "User-Agent": USER_AGENT,
                    "Content-Type": "application/json; charset=utf-8",
                    "Accept": "application/json, text/javascript, */*; q=0.01",
                    "X-Requested-With": "XMLHttpRequest",
                    "Referer": course_url
                }
            )
            raw_att_str = None
            try:
                with opener.open(req_att, timeout=15) as resp:
                    raw_att_str = resp.read().decode('utf-8')
            except Exception:
                # Try POST fallback
                req_att_post = urllib.request.Request(
                    f"{BASE_URL}/STCoursepage.aspx/GetAttendanceList",
                    data=json.dumps({"TCInfoID": tc_id}).encode('utf-8'),
                    headers={
                        "User-Agent": USER_AGENT,
                        "Content-Type": "application/json; charset=utf-8",
                        "X-Requested-With": "XMLHttpRequest",
                        "Referer": course_url
                    }
                )
                with opener.open(req_att_post, timeout=15) as resp:
                    raw_att_str = resp.read().decode('utf-8')

            if raw_att_str:
                obj = json.loads(raw_att_str)
                d_str = obj.get("d", "")
                if d_str:
                    a_data = json.loads(d_str)
                    aa_data = a_data.get("aaData") or []
                    for row in aa_data:
                        course["attendanceList"].append({
                            "dayName": row.get("PresentDay", ""),
                            "startTime": row.get("PresentStartTime", ""),
                            "insertDate": row.get("InsertDate", ""),
                            "status": bool(row.get("Status", True)),
                            "courseName": course["name"],
                            "tcInfoId": tc_id
                        })
        except Exception:
            pass

    return courses


def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip


def run_server(port=8080):
    web_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(web_dir)

    server_address = ('0.0.0.0', port)
    httpd = ThreadedHTTPServer(server_address, ElearnProxyHandler)

    local_ip = get_local_ip()

    print("=" * 60)
    print("🚀 سامانه ایلرن! (نسخه وب‌اپلیکیشن iOS / کامپیوتر)")
    print("=" * 60)
    print(f"📱 جهت باز کردن در آیفون (Safari):  http://{local_ip}:{port}")
    print(f"💻 جهت باز کردن در کامپیوتر:         http://localhost:{port}")
    print("=" * 60)
    print("📌 برای افزودن به صفحه اصلی آیفون:")
    print("   1. آدرس بالا را در Safari آیفون باز کنید.")
    print("   2. دکمه Share (مربع با فلش رو به بالا) را بزنید.")
    print("   3. گزینه 'Add to Home Screen' (افزودن به صفحه اصلی) را انتخاب کنید.")
    print("=" * 60)
    print("برای توقف سرور کلیدهای Ctrl+C را فشار دهید...\n")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nسرور متوقف شد.")
        httpd.server_close()


if __name__ == '__main__':
    port = 8080
    if len(sys.argv) > 1:
        try:
            port = int(sys.argv[1])
        except ValueError:
            pass
    run_server(port)
