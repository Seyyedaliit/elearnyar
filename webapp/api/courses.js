const https = require('https');
const crypto = require('crypto');

const BASE_URL = 'elearn.uk.ac.ir';
const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';

const httpsAgent = new https.Agent({
  rejectUnauthorized: false,
  secureOptions: crypto.constants.SSL_OP_LEGACY_SERVER_CONNECT
});

function doRequest(options, postData = null, cookieJar = []) {
  return new Promise((resolve, reject) => {
    const headers = options.headers || {};
    if (cookieJar.length > 0) {
      headers['Cookie'] = cookieJar.join('; ');
    }
    headers['User-Agent'] = USER_AGENT;

    const reqOptions = {
      hostname: BASE_URL,
      port: 443,
      path: options.path,
      method: options.method || 'GET',
      headers: headers,
      agent: httpsAgent,
      timeout: 15000
    };

    const req = https.request(reqOptions, (res) => {
      const setCookies = res.headers['set-cookie'];
      if (setCookies) {
        setCookies.forEach((sc) => {
          const cookiePart = sc.split(';')[0];
          cookieJar.push(cookiePart);
        });
      }

      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        resolve({ statusCode: res.statusCode, headers: res.headers, body: data });
      });
    });

    req.on('error', (err) => reject(err));
    req.on('timeout', () => {
      req.destroy();
      reject(new Error('Request to elearn.uk.ac.ir timed out (ممکن است دسترسی از خارج کشور مسدود باشد)'));
    });

    if (postData) {
      req.write(postData);
    }
    req.end();
  });
}

module.exports = async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  if (req.method === 'GET') {
    // Health check test
    try {
      const ping = await doRequest({ path: '/index.aspx', method: 'GET' }, null, []);
      return res.status(200).json({ status: 'ok', elearnStatusCode: ping.statusCode });
    } catch (err) {
      return res.status(500).json({ error: err.message });
    }
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  const { username, password } = req.body || {};
  if (!username || !password) {
    return res.status(400).json({ error: 'نام کاربری و کلمه عبور الزامی است.' });
  }

  const cookieJar = [];

  try {
    // 1. Visit index.aspx
    await doRequest({ path: '/index.aspx', method: 'GET' }, null, cookieJar);

    // 2. Login
    const loginPayload = JSON.stringify({
      valuser: username,
      valpass: password,
      setpass: "0"
    });

    const loginRes = await doRequest({
      path: '/Index.aspx/get_login',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'X-Requested-With': 'XMLHttpRequest',
        'Referer': 'https://elearn.uk.ac.ir/index.aspx'
      }
    }, loginPayload, cookieJar);

    const loginData = JSON.parse(loginRes.body);
    if (!loginData.d || loginData.d.Item1 !== 'ok') {
      const msg = (loginData.d && loginData.d.Item2) ? loginData.d.Item2 : 'نام کاربری یا کلمه عبور اشتباه است.';
      return res.status(401).json({ error: msg });
    }

    // 3. Visit STClassList.aspx
    await doRequest({
      path: '/STClassList.aspx',
      method: 'GET',
      headers: { 'Referer': 'https://elearn.uk.ac.ir/index.aspx' }
    }, null, cookieJar);

    // 4. Get Courses
    const coursesRes = await doRequest({
      path: '/STClasslist.aspx/GetSTClasses',
      method: 'GET',
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'X-Requested-With': 'XMLHttpRequest',
        'Referer': 'https://elearn.uk.ac.ir/STClassList.aspx'
      }
    }, null, cookieJar);

    const coursesRawObj = JSON.parse(coursesRes.body);
    const coursesRawList = JSON.parse(coursesRawObj.d || '[]');

    const courses = [];
    for (const item of coursesRawList) {
      courses.push({
        tcInfoId: String(item.TCInfoID || ''),
        name: item.classname || '',
        groupNo: String(item.LessonGroupNo || ''),
        unitType: item.unittype || 1,
        teacherName: item.TeacherName || '',
        exercisesCount: item.Exercisecount || 0,
        learnCount: item.learncount || 0,
        unreadMessages: item.messagecount || 0,
        exercises: [],
        learningFiles: [],
        messages: [],
        attendanceList: []
      });
    }

    // 5. Fetch details for each course
    for (const c of courses) {
      if (!c.tcInfoId) continue;
      const courseUrl = `https://elearn.uk.ac.ir/STCoursepage.aspx?CLid=${encodeURIComponent(c.tcInfoId)}`;

      try {
        await doRequest({ path: `/STCoursepage.aspx?CLid=${encodeURIComponent(c.tcInfoId)}`, method: 'GET' }, null, cookieJar);
      } catch (e) {}

      // GetInfo
      try {
        const infoRes = await doRequest({
          path: '/STCoursepage.aspx/GetInfo',
          method: 'POST',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'X-Requested-With': 'XMLHttpRequest',
            'Referer': courseUrl
          }
        }, '{}', cookieJar);
        const infoObj = JSON.parse(infoRes.body);
        const infoArr = JSON.parse(infoObj.d || '[]');
        if (infoArr.length > 0) {
          if (infoArr[0].teachername) c.teacherName = infoArr[0].teachername;
          c.examDate = infoArr[0].ExamPersianDate || '';
          c.examTime = infoArr[0].ExamStartTime || '';
        }
      } catch (e) {}

      // ExerciseFileList
      try {
        const exRes = await doRequest({
          path: '/STCoursepage.aspx/ExerciseFileList',
          method: 'POST',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'X-Requested-With': 'XMLHttpRequest',
            'Referer': courseUrl
          }
        }, JSON.stringify({ startpage: '0', rowcount: '50' }), cookieJar);
        const exObj = JSON.parse(exRes.body);
        const exData = JSON.parse(exObj.d || '{}');
        const aaData = exData.aaData || [];
        aaData.forEach((row) => {
          c.exercises.push({
            exid: String(row.exid || ''),
            fid: row.fid || 0,
            title: row.extitle || '',
            description: row.exDescription || '',
            startDate: row.StartDate || '',
            endDate: row.EndDate || '',
            grade: row.Grade || 0.0,
            exGuid: row.ExGuid || '',
            fGuid: row.FGuid || '',
            filePath: row.lfilepath || '',
            courseName: c.name,
            tcInfoId: c.tcInfoId
          });
        });
      } catch (e) {}

      // LearningFileList
      try {
        const lfRes = await doRequest({
          path: '/STCoursepage.aspx/LearningFileList',
          method: 'POST',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'X-Requested-With': 'XMLHttpRequest',
            'Referer': courseUrl
          }
        }, JSON.stringify({ startpage: '0', rowcount: '50' }), cookieJar);
        const lfObj = JSON.parse(lfRes.body);
        const lfData = JSON.parse(lfObj.d || '{}');
        const aaData = lfData.aaData || [];
        aaData.forEach((row) => {
          c.learningFiles.push({
            lid: String(row.lid || ''),
            fid: row.fid || 0,
            title: row.ltitle || '',
            description: row.lDescription || '',
            insertDate: row.insertdate || '',
            fGuid: row.FGuid || '',
            leGuid: row.LeGuid || '',
            filePath: row.lfilepath || '',
            courseName: c.name,
            tcInfoId: c.tcInfoId
          });
        });
      } catch (e) {}

      // Messages
      try {
        let msgRes = await doRequest({
          path: `/STCoursepage.aspx/GetMessageList?TCInfoID=${encodeURIComponent(c.tcInfoId)}`,
          method: 'GET',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'X-Requested-With': 'XMLHttpRequest',
            'Referer': courseUrl
          }
        }, null, cookieJar);

        if (msgRes.statusCode !== 200) {
          msgRes = await doRequest({
            path: '/STCoursepage.aspx/GetMessageList',
            method: 'POST',
            headers: {
              'Content-Type': 'application/json; charset=utf-8',
              'X-Requested-With': 'XMLHttpRequest',
              'Referer': courseUrl
            }
          }, JSON.stringify({ TCInfoID: c.tcInfoId }), cookieJar);
        }

        const msgObj = JSON.parse(msgRes.body);
        const dStr = msgObj.d || '';
        if (dStr) {
          const mData = JSON.parse(dStr);
          const aaData = mData.aaData || [];
          aaData.forEach((row, idx) => {
            c.messages.push({
              id: String(row.Id || row.id || idx + 1),
              title: row.Title || row.title || '',
              senderName: row.fullname || row.FullName || c.teacherName,
              insertDate: row.InsertDate || row.insertdate || '',
              message: row.Message || row.message || '',
              isRead: (row.isread === 1),
              courseName: c.name,
              tcInfoId: c.tcInfoId
            });
          });
        }
      } catch (e) {}

      // Attendance
      try {
        let attRes = await doRequest({
          path: `/STCoursepage.aspx/GetAttendanceList?TCInfoID=${encodeURIComponent(c.tcInfoId)}`,
          method: 'GET',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'X-Requested-With': 'XMLHttpRequest',
            'Referer': courseUrl
          }
        }, null, cookieJar);

        if (attRes.statusCode !== 200) {
          attRes = await doRequest({
            path: '/STCoursepage.aspx/GetAttendanceList',
            method: 'POST',
            headers: {
              'Content-Type': 'application/json; charset=utf-8',
              'X-Requested-With': 'XMLHttpRequest',
              'Referer': courseUrl
            }
          }, JSON.stringify({ TCInfoID: c.tcInfoId }), cookieJar);
        }

        const attObj = JSON.parse(attRes.body);
        const dStr = attObj.d || '';
        if (dStr) {
          const aData = JSON.parse(dStr);
          const aaData = aData.aaData || [];
          aaData.forEach((row) => {
            c.attendanceList.push({
              dayName: row.PresentDay || '',
              startTime: row.PresentStartTime || '',
              insertDate: row.InsertDate || '',
              status: Boolean(row.Status !== undefined ? row.Status : true),
              courseName: c.name,
              tcInfoId: c.tcInfoId
            });
          });
        }
      } catch (e) {}
    }

    return res.status(200).json({ status: 'ok', courses });
  } catch (err) {
    console.error('API courses error:', err);
    return res.status(500).json({ error: err.message || 'خطای اتصال به سرور' });
  }
};
