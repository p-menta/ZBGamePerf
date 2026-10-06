local function onKeyPressed(key)
    local isCtrl = isKeyDown(Keyboard.KEY_LCONTROL) or isKeyDown(Keyboard.KEY_RCONTROL)
    if key == Keyboard.KEY_1 and isCtrl then
        ZBGamePerf.cycleOSDMode()
    elseif key == Keyboard.KEY_2 and isCtrl then
        ZBGamePerf.cycleModDataMode()
    elseif key == Keyboard.KEY_0 and isCtrl then
        ZBGamePerf.resetStats()
    elseif getCore():isKey("Toggle ZBGamePerf OSD", key) then
        ZBGamePerf.cycleOSDMode()
    elseif getCore():isKey("Freeze ZBGamePerf OSD", key) then
        ZBGamePerf.toggleOSDFreeze()
    end
end

Events.OnKeyPressed.Add(onKeyPressed)
