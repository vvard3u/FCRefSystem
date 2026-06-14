
// BK Use Case Clickable Prototype
// Fixed for older Figma plugin JS runtime: no nullish coalescing anywhere.
// Run in Figma: Plugins -> Development -> Import plugin from manifest...

const C = {
  bg: '#F6F8FB',
  card: '#FFFFFF',
  border: '#D9E1EC',
  text: '#172033',
  muted: '#64748B',
  blue: '#2563EB',
  blueSoft: '#EAF1FF',
  green: '#16A34A',
  greenSoft: '#EAF8EF',
  red: '#DC2626',
  redSoft: '#FEF2F2',
  amber: '#F59E0B',
  amberSoft: '#FFF7ED',
};

const W = 1366;
const H = 768;
const SIDEBAR = 240;

function hexToRgb(hex) {
  const v = hex.replace('#', '');
  const bigint = parseInt(v, 16);
  return {
    r: ((bigint >> 16) & 255) / 255,
    g: ((bigint >> 8) & 255) / 255,
    b: (bigint & 255) / 255,
  };
}

function fill(hex) {
  return [{ type: 'SOLID', color: hexToRgb(hex) }];
}

function stroke(hex) {
  return [{ type: 'SOLID', color: hexToRgb(hex) }];
}

function rect(parent, x, y, w, h, opts = {}) {
  const r = figma.createRectangle();
  r.x = x; r.y = y; r.resize(w, h);
  r.cornerRadius = (opts.radius !== undefined ? opts.radius : 10);
  r.fills = fill((opts.fill !== undefined ? opts.fill : C.card));
  if (opts.stroke) {
    r.strokes = stroke(opts.stroke);
    r.strokeWeight = (opts.strokeWeight !== undefined ? opts.strokeWeight : 1);
  } else {
    r.strokes = [];
  }
  if (opts.name) r.name = opts.name;
  parent.appendChild(r);
  return r;
}

function line(parent, x1, y1, x2, y2, color = C.border, weight = 1) {
  const r = figma.createRectangle();
  r.x = Math.min(x1, x2);
  r.y = Math.min(y1, y2);
  r.resize(Math.max(Math.abs(x2 - x1), weight), Math.max(Math.abs(y2 - y1), weight));
  r.fills = fill(color);
  r.strokes = [];
  parent.appendChild(r);
  return r;
}

async function txt(parent, x, y, s, opts = {}) {
  const t = figma.createText();
  t.x = x; t.y = y;
  t.characters = s;
  t.fontName = { family: 'Inter', style: opts.bold ? 'Bold' : (opts.medium ? 'Medium' : 'Regular') };
  t.fontSize = (opts.size !== undefined ? opts.size : 14);
  t.fills = fill((opts.color !== undefined ? opts.color : C.text));
  if (opts.width) {
    t.resize(opts.width, (opts.height !== undefined ? opts.height : 24));
    t.textAutoResize = 'HEIGHT';
  }
  if (opts.align) t.textAlignHorizontal = opts.align;
  parent.appendChild(t);
  return t;
}

async function button(parent, x, y, w, h, label, opts = {}) {
  const bg = opts.danger ? C.red : (opts.primary === false ? '#FFFFFF' : C.blue);
  const br = opts.danger ? C.red : (opts.primary === false ? C.border : C.blue);
  const color = opts.primary === false ? C.text : '#FFFFFF';
  const group = figma.createFrame();
  group.name = opts.name || `Button / ${label}`;
  group.x = x; group.y = y; group.resize(w, h);
  group.fills = [];
  group.clipsContent = false;
  parent.appendChild(group);
  rect(group, 0, 0, w, h, { fill: bg, stroke: br, radius: 8 });
  const tx = await txt(group, 0, h/2 - 9, label, { size: 14, bold: true, color, width: w, align: 'CENTER' });
  return group;
}

async function pill(parent, x, y, w, h, label, color, bg) {
  const f = figma.createFrame();
  f.name = `Badge / ${label}`;
  f.x = x; f.y = y; f.resize(w, h); f.fills = []; f.clipsContent = false;
  parent.appendChild(f);
  rect(f, 0, 0, w, h, { fill: bg, radius: h/2 });
  await txt(f, 0, h/2 - 8, label, { size: 12, bold: true, color, width: w, align: 'CENTER' });
  return f;
}

async function field(parent, x, y, w, label, value, opts = {}) {
  await txt(parent, x, y, label + (opts.required ? ' *' : ''), { size: 12, bold: true, color: C.muted });
  rect(parent, x, y + 20, w, (opts.height !== undefined ? opts.height : 42), { fill: '#FFFFFF', stroke: C.border, radius: 8 });
  await txt(parent, x + 14, y + 32, value, { size: 14, color: opts.placeholder ? C.muted : C.text, width: w - 28 });
}

async function sidebar(frame, active) {
  rect(frame, 0, 0, SIDEBAR, H, { fill: '#FFFFFF', stroke: C.border, radius: 0 });
  rect(frame, 24, 26, 36, 36, { fill: C.blue, radius: 8 });
  await txt(frame, 72, 29, 'Система отбора', { size: 15, bold: true });
  await txt(frame, 72, 49, 'кандидатов в БК', { size: 14, bold: true });
  const items = ['Главная','Кандидаты','Регламент','Приглашения','Голосование','Журнал','Настройки'];
  let y = 118;
  const nav = {};
  for (const item of items) {
    const bg = item === active ? C.blueSoft : '#FFFFFF';
    const color = item === active ? C.blue : C.muted;
    const hit = figma.createFrame();
    hit.name = `Nav / ${item}`;
    hit.x = 14; hit.y = y - 28; hit.resize(205, 42);
    hit.fills = []; hit.clipsContent = false;
    frame.appendChild(hit);
    rect(hit, 0, 0, 205, 42, { fill: bg, radius: 8 });
    await txt(hit, 22, 12, item, { size: 14, bold: item === active, color });
    nav[item] = hit;
    y += 54;
  }
  await txt(frame, 36, H - 52, '‹  Свернуть', { size: 14, color: C.muted });
  return nav;
}

async function topbar(frame, title, role) {
  rect(frame, SIDEBAR, 0, W - SIDEBAR, 72, { fill: '#FFFFFF', stroke: C.border, radius: 0 });
  await txt(frame, SIDEBAR + 34, 28, title, { size: 22, bold: true });
  rect(frame, W - 148, 22, 38, 38, { fill: C.blueSoft, stroke: C.border, radius: 19 });
  await txt(frame, W - 139, 33, 'ИА', { size: 13, bold: true, color: C.blue });
  await txt(frame, W - 100, 22, 'Иванова А. С.', { size: 14, bold: true });
  await txt(frame, W - 100, 43, role, { size: 12, color: C.muted });
}

async function baseFrame(name, title, role, active) {
  const f = figma.createFrame();
  f.name = name;
  f.resize(W, H);
  f.fills = fill(C.bg);
  f.clipsContent = true;
  figma.currentPage.appendChild(f);
  const nav = await sidebar(f, active);
  await topbar(f, title, role);
  return { f, nav };
}

function setReaction(node, destinationFrame) {
  try {
    node.reactions = [{
      trigger: { type: 'ON_CLICK' },
      action: {
        type: 'NODE',
        destinationId: destinationFrame.id,
        navigation: 'NAVIGATE',
        transition: { type: 'INSTANT' },
      },
    }];
  } catch (e) {
    node.setPluginData('prototype-destination', destinationFrame.name);
  }
}

async function UC01() {
  const { f, nav } = await baseFrame('UC-01 / Создание реферального приглашения', 'Создание реферального приглашения', 'Участник', 'Приглашения');
  await txt(f, 280, 98, 'Приглашения / Создание реферального приглашения', { size: 13, color: C.muted });
  rect(f, 280, 124, 1000, 92, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await txt(f, 310, 150, 'Доступная квота', { size: 13, bold: true, color: C.muted });
  await txt(f, 310, 174, '3', { size: 32, bold: true });
  line(f, 410, 146, 410, 194);
  await txt(f, 440, 150, 'Срок действия приглашения определяется системой автоматически.', { size: 15, bold: true });
  await txt(f, 440, 176, 'После создания ссылка становится активной и доступной для передачи кандидату.', { size: 14, color: C.muted });

  rect(f, 280, 250, 430, 255, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await txt(f, 310, 282, 'Создать приглашение', { size: 20, bold: true });
  await field(f, 310, 320, 340, 'Комментарий', 'Кратко укажите основание приглашения', { height: 78, placeholder: true });
  const createBtn = await button(f, 310, 440, 190, 42, 'Создать приглашение');

  rect(f, 740, 250, 540, 255, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await pill(f, 1140, 278, 95, 28, 'Активно', C.green, C.greenSoft);
  await txt(f, 770, 288, 'Приглашение создано', { size: 20, bold: true });
  await field(f, 770, 325, 420, 'Ссылка приглашения', 'https://bk.example/inv/9f4a2c7b');
  await txt(f, 770, 420, 'Срок действия', { size: 14, bold: true, color: C.muted });
  await txt(f, 920, 420, '30 дней с момента создания', { size: 14 });
  await txt(f, 770, 452, 'Пригласивший участник', { size: 14, bold: true, color: C.muted });
  await txt(f, 950, 452, 'Иванова А. С.', { size: 14 });

  rect(f, 280, 535, 1000, 150, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await txt(f, 310, 568, 'Недавние приглашения', { size: 18, bold: true });
  await txt(f, 310, 606, 'Ссылка', { size: 12, bold: true, color: C.muted });
  await txt(f, 710, 606, 'Статус', { size: 12, bold: true, color: C.muted });
  await txt(f, 860, 606, 'Создано', { size: 12, bold: true, color: C.muted });
  line(f, 310, 620, 1245, 620);
  await txt(f, 310, 646, 'bk.example/inv/9f4a2c7b', { size: 14, color: C.blue });
  await txt(f, 710, 646, 'Активно', { size: 14, color: C.green });
  await txt(f, 860, 646, '16.05.2025, 11:24', { size: 14 });
  return { frame: f, nav, actions: { createBtn } };
}

async function UC02() {
  const { f, nav } = await baseFrame('UC-02 / Создание регламента отбора', 'Создание регламента отбора', 'Администратор', 'Регламент');
  await txt(f, 280, 98, 'Регламент / Создание регламента отбора', { size: 13, color: C.muted });
  rect(f, 280, 124, 620, 150, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await field(f, 310, 150, 520, 'Название регламента', 'Регламент вступления в закрытое сообщество', { required: true });
  await field(f, 310, 210, 520, 'Описание', 'Последовательность этапов, лимиты, пороги и правила отбора кандидатов');
  rect(f, 930, 124, 350, 150, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await txt(f, 960, 160, 'Проверка регламента', { size: 18, bold: true });
  await pill(f, 960, 184, 70, 28, '5/5', C.green, C.greenSoft);
  await txt(f, 960, 228, 'Регламент готов к сохранению', { size: 14, color: C.muted });

  rect(f, 280, 300, 1000, 260, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await txt(f, 310, 335, 'Этапы отбора', { size: 20, bold: true });
  const addStage = await button(f, 1110, 318, 140, 38, 'Добавить этап', { primary: false });
  const headers = [['№',310],['Этап',365],['Тип',610],['Лимит',760],['Порог',870],['Активен',980]];
  for (const [h,x] of headers) await txt(f, x, 385, h, { size: 12, bold: true, color: C.muted });
  line(f, 310, 400, 1245, 400);
  const rows = [
    ['1','Анкета кандидата','Форма','1','—','Да'],
    ['2','Первое испытание','Задание','2','70%','Да'],
    ['3','Интервью с наставником','Интервью','1','—','Да'],
    ['4','Голосование участников','Голосование','1','60%','Да'],
  ];
  let y = 430;
  for (const r of rows) {
    for (let i=0; i<r.length; i++) await txt(f, headers[i][1], y, r[i], { size: 14, color: r[i] === 'Да' ? C.green : C.text });
    line(f, 310, y + 20, 1245, y + 20);
    y += 42;
  }
  rect(f, 280, 585, 1000, 70, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await txt(f, 310, 610, 'Правила прохождения: кандидат переходит далее после успешного завершения обязательного этапа.', { size: 14 });
  const saveBtn = await button(f, 310, 632, 185, 40, 'Сохранить регламент');
  return { frame: f, nav, actions: { addStage, saveBtn } };
}

async function UC03() {
  const { f, nav } = await baseFrame('UC-03 / Голосование за кандидата', 'Голосование по кандидату', 'Участник с привилегиями', 'Голосование');
  await txt(f, 280, 98, 'Кандидаты / Голосование по кандидату', { size: 13, color: C.muted });
  rect(f, 280, 124, 1000, 110, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  rect(f, 310, 150, 60, 60, { fill: C.blueSoft, stroke: C.border, radius: 12 });
  await txt(f, 332, 166, 'К', { size: 26, bold: true, color: C.blue });
  await txt(f, 395, 150, 'Петрова Мария Сергеевна', { size: 20, bold: true });
  await pill(f, 395, 180, 140, 28, 'На рассмотрении', C.amber, C.amberSoft);
  await txt(f, 395, 218, 'Текущий этап: Голосование участников', { size: 14, color: C.muted });
  await txt(f, 690, 160, 'Этапы: Анкета → Испытание → Интервью → Голосование', { size: 14 });
  await txt(f, 690, 194, 'Пригласил: Иванова А. С.', { size: 14, color: C.muted });

  rect(f, 280, 260, 260, 280, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await txt(f, 310, 296, 'Материалы кандидата', { size: 18, bold: true });
  for (const [i, t] of ['Анкета кандидата','Ответ по испытанию','Отчет интервьюера','История этапов'].entries()) {
    await txt(f, 310, 338 + i*45, t, { size: 14 });
    await txt(f, 445, 338 + i*45, 'Открыть', { size: 14, color: C.blue });
    line(f, 310, 354 + i*45, 510, 354 + i*45);
  }

  rect(f, 575, 260, 410, 360, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await txt(f, 605, 300, 'Ваше голосование', { size: 20, bold: true });
  await pill(f, 605, 324, 100, 28, 'Открыто', C.green, C.greenSoft);
  await txt(f, 605, 388, 'Поддерживаете ли вы принятие кандидата в сообщество?', { size: 15, bold: true });
  rect(f, 605, 420, 155, 48, { fill: C.blueSoft, stroke: C.blue, radius: 8 });
  await txt(f, 632, 436, 'Поддержать', { size: 14, bold: true, color: C.blue });
  rect(f, 775, 420, 155, 48, { fill: '#FFFFFF', stroke: C.border, radius: 8 });
  await txt(f, 800, 436, 'Не поддержать', { size: 14, bold: true, color: C.muted });
  await field(f, 605, 495, 320, 'Пояснение выбора', 'Кратко обоснуйте решение', { required: true, height: 70, placeholder: true });
  const voteBtn = await button(f, 605, 590, 160, 42, 'Отправить голос');

  rect(f, 1010, 260, 270, 240, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await txt(f, 1040, 300, 'Информация', { size: 18, bold: true });
  await txt(f, 1040, 340, 'Срок: 20.05.2025', { size: 14, color: C.muted });
  await txt(f, 1040, 375, 'Голосов: 3 из 7', { size: 14, bold: true });
  await txt(f, 1040, 420, 'Один участник с привилегиями\nможет подать один голос.', { size: 14, color: C.muted, width: 200 });
  return { frame: f, nav, actions: { voteBtn } };
}

async function UC04() {
  const { f, nav } = await baseFrame('UC-04 / Блокировка кандидата', 'Блокировка кандидата', 'Интервьюер', 'Кандидаты');
  await txt(f, 280, 98, 'Кандидаты / Блокировка кандидата', { size: 13, color: C.muted });
  rect(f, 280, 124, 1000, 100, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  rect(f, 310, 150, 54, 54, { fill: C.blueSoft, stroke: C.border, radius: 12 });
  await txt(f, 325, 164, 'К', { size: 24, bold: true, color: C.blue });
  await txt(f, 390, 150, 'Смирнова Анна Сергеевна', { size: 20, bold: true });
  await txt(f, 390, 190, 'Статус: В процессе      Текущий этап: Интервью с наставником', { size: 14, color: C.muted });

  rect(f, 280, 260, 500, 370, { fill: '#FFFFFF', stroke: C.red, strokeWeight: 1.2, radius: 12 });
  await txt(f, 310, 300, 'Заблокировать кандидата', { size: 20, bold: true });
  await txt(f, 310, 332, 'Блокировка останавливает процесс отбора кандидата.', { size: 14, color: C.muted });
  await field(f, 310, 370, 390, 'Категория причины', 'Нарушение правил сообщества', { required: true });
  await field(f, 310, 440, 390, 'Подробная причина', 'Опишите основание блокировки', { required: true, height: 88, placeholder: true });
  await txt(f, 310, 560, 'Дата и время фиксируются системой автоматически.', { size: 13, color: C.muted });
  const blockBtn = await button(f, 310, 590, 230, 42, 'Заблокировать кандидата', { danger: true });
  await button(f, 555, 590, 100, 42, 'Отмена', { primary: false });

  rect(f, 820, 260, 460, 330, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await txt(f, 850, 300, 'Последствия блокировки', { size: 20, bold: true });
  for (const [i, t] of ['Статус кандидата изменится на Blocked.','Процесс отбора будет остановлен.','Будет сформирован отчет для администратора.','Событие будет записано в журнал.'].entries()) {
    await txt(f, 850, 345 + i*38, '• ' + t, { size: 14, color: C.muted });
  }
  rect(f, 850, 520, 350, 50, { fill: C.redSoft, stroke: C.border, radius: 8 });
  await txt(f, 870, 536, 'Требуется обязательное основание.', { size: 14, bold: true, color: C.red });
  return { frame: f, nav, actions: { blockBtn } };
}

async function UC05() {
  const { f, nav } = await baseFrame('UC-05 / Пройти этап отбора', 'Прохождение этапа отбора', 'Кандидат', 'Кандидаты');
  rect(f, 280, 100, 1000, 92, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await txt(f, 310, 130, 'Этапы отбора', { size: 18, bold: true });
  const steps = [['Анкета', C.green], ['Испытание', C.blue], ['Интервью', C.muted], ['Голосование', C.muted]];
  let x = 360;
  for (let i=0; i<steps.length; i++) {
    const [s, c] = steps[i];
    rect(f, x, 145, 28, 28, { fill: i < 2 ? c : '#FFFFFF', stroke: c, radius: 14 });
    await txt(f, x + 9, 151, i === 0 ? '✓' : String(i + 1), { size: 14, bold: true, color: i < 2 ? '#FFFFFF' : C.muted });
    await txt(f, x - 24, 180, s, { size: 13, bold: true });
    if (i < steps.length - 1) line(f, x + 38, 159, x + 170, 159, i === 0 ? C.blue : C.border, 2);
    x += 200;
  }
  rect(f, 280, 225, 640, 330, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await txt(f, 310, 264, 'Первое испытание', { size: 22, bold: true });
  await pill(f, 760, 242, 110, 28, 'Текущий этап', C.blue, C.blueSoft);
  await txt(f, 310, 305, 'Срок выполнения определяется регламентом.', { size: 14, color: C.muted });
  await txt(f, 310, 346, 'Инструкция', { size: 16, bold: true });
  await txt(f, 310, 374, 'Выполните задание и отправьте результат через форму ниже.', { size: 14, color: C.muted });
  await field(f, 310, 420, 520, 'Ваш ответ', 'Опишите выполненное действие и приложите материалы', { required: true, height: 74, placeholder: true });
  rect(f, 310, 530, 520, 55, { fill: '#FFFFFF', stroke: C.border, radius: 8 });
  await txt(f, 520, 548, 'Загрузить файл', { size: 14, bold: true, color: C.blue });
  const submitBtn = await button(f, 310, 610, 180, 42, 'Отправить результат');

  rect(f, 950, 225, 330, 220, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await txt(f, 980, 264, 'Статус этапа', { size: 18, bold: true });
  await pill(f, 980, 290, 110, 28, 'В процессе', C.blue, C.blueSoft);
  await txt(f, 980, 350, 'Попытка: 1 из 2', { size: 14, bold: true });
  await txt(f, 980, 388, 'После отправки результат\nбудет передан на проверку.', { size: 14, color: C.muted, width: 220 });
  rect(f, 950, 470, 330, 115, { fill: '#FFFFFF', stroke: C.border, radius: 12 });
  await txt(f, 980, 510, 'История попыток', { size: 18, bold: true });
  await txt(f, 980, 548, 'Попыток пока нет', { size: 14, color: C.muted });
  return { frame: f, nav, actions: { submitBtn } };
}

async function main() {
  await figma.loadFontAsync({ family: 'Inter', style: 'Regular' });
  await figma.loadFontAsync({ family: 'Inter', style: 'Medium' });
  await figma.loadFontAsync({ family: 'Inter', style: 'Bold' });

  figma.currentPage.name = 'BK clickable prototype';
  figma.currentPage.children.forEach(n => n.remove());

  const s1 = await UC01();
  const s2 = await UC02();
  const s3 = await UC03();
  const s4 = await UC04();
  const s5 = await UC05();

  const screens = [s1, s2, s3, s4, s5];
  screens.forEach((s, i) => { s.frame.x = (i % 2) * (W + 80); s.frame.y = Math.floor(i / 2) * (H + 80); });

  // Sidebar navigation.
  for (const s of screens) {
    setReaction(s.nav['Приглашения'], s1.frame);
    setReaction(s.nav['Регламент'], s2.frame);
    setReaction(s.nav['Голосование'], s3.frame);
    setReaction(s.nav['Кандидаты'], s5.frame);
    setReaction(s.nav['Журнал'], s5.frame);
  }

  // Main scenario actions.
  setReaction(s1.actions.createBtn, s2.frame);
  setReaction(s2.actions.saveBtn, s5.frame);
  setReaction(s3.actions.voteBtn, s5.frame);
  setReaction(s4.actions.blockBtn, s5.frame);
  setReaction(s5.actions.submitBtn, s3.frame);

  try {
    figma.currentPage.prototypeStartNode = s1.frame;
  } catch (e) {}

  figma.viewport.scrollAndZoomIntoView(screens.map(s => s.frame));
  figma.notify('Кликабельный прототип создан: 5 экранов, навигация и основные переходы.');
  figma.closePlugin();
}

main().catch(err => {
  console.error(err);
  figma.notify('Ошибка создания прототипа: ' + err.message);
  figma.closePlugin();
});
