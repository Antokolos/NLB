log = obj {
    nam = 'log';
    system_type = true;
    TRACE = 0;
    DEBUG = 1;
    INFO = 2;
    WARN = 3;
    ERROR = 4;
    loglevel = 2;
    set_level = function(s, level)
        s.level = level;
    end;
    do_log = function(s, loglevel, message)
        if loglevel >= s.loglevel then
            print(tostring(os.clock()) .. " " .. message);
        end
    end;
    trace = function(s, message)
        return s:do_log(s.TRACE, "TRACE: " .. message);
    end;
    dbg = function(s, message)
        return s:do_log(s.DEBUG, "DEBUG: " .. message);
    end;
    info = function(s, message)
        return s:do_log(s.INFO, "INFO: " .. message);
    end;
    warn = function(s, message)
        return s:do_log(s.WARN, "WARN: " .. message);
    end;
    err = function(s, message)
        return s:do_log(s.ERROR, "ERROR: " .. message);
    end;
}

stead.module_init(function()
    log:set_level(log.INFO);
end)