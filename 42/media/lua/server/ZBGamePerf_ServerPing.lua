-- ZBGamePerf Server-Side Ping/Pong Handler
-- Replies to client "Ping" command under module "ZBGamePerf"
-- Used to measure real server simulation round-trip under tick load.

if isServer() then
    local function onClientCommand(module, command, player, args)
        if module == "ZBGamePerf" and command == "Ping" then
            sendServerCommand(player, "ZBGamePerf", "Pong", args or {})
        end
    end

    Events.OnClientCommand.Add(onClientCommand)
end
