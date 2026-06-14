# BK Use Case Clickable Prototype

Это не статичные картинки, а Figma plugin, который создаёт в текущем Figma-файле 5 экранов и настраивает кликабельные переходы.

## Как запустить

1. Распакуйте архив.
2. Откройте Figma Desktop.
3. Создайте новый Figma-файл.
4. Меню Figma: Plugins → Development → Import plugin from manifest...
5. Выберите `manifest.json` из распакованной папки.
6. Запустите plugin: Plugins → Development → BK Use Case Clickable Prototype.
7. В файле появится страница `BK clickable prototype` с 5 экранами.
8. Нажмите Present / Prototype preview, чтобы проверить кликабельность.

## Экраны

- UC-01 / Создание реферального приглашения
- UC-02 / Создание регламента отбора
- UC-03 / Голосование за кандидата
- UC-04 / Блокировка кандидата
- UC-05 / Пройти этап отбора

## Важные правки, учтённые в макете

- Интерфейс упрощён под дальнейшую реализацию.
- Срок действия приглашения определяется системой, а не пользователем.
- Предметная область — закрытое сообщество / БК, не найм на работу.
- Макеты создаются в Figma как редактируемые слои.
- Основные кнопки и пункты меню имеют prototype transitions.


## Исправление версии fixed

Эта версия исправляет ошибку Figma plugin sandbox:

`Syntax error on line 46: Unexpected token ?`

Причина: runtime Figma в данном окружении не принял оператор nullish coalescing `??`.
В `code.js` он заменён на совместимые проверки вида:

`opts.radius !== undefined ? opts.radius : 10`


## Версия es5_fixed

В предыдущей fixed-версии был исправлен не весь синтаксис `??`.
В этой версии из `code.js` удалены все операторы `??`, включая:

- `opts.fill ?? C.card`
- `opts.color ?? C.text`
- `opts.height ?? 24`
- `opts.height ?? 42`

Если Figma продолжала показывать `Unexpected token ?`, используй именно этот архив.
