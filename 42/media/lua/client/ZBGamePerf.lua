local function onKeyPressed(key)
    if getCore():isKey("Toggle ZBGamePerf OSD", key) then
        ZBGamePerf.toggleOSD()
    elseif getCore():isKey("Freeze ZBGamePerf OSD", key) then
        ZBGamePerf.toggleOSDFreeze()
    end
end

Events.OnKeyPressed.Add(onKeyPressed)
