package ru.wink.winkaipreviz.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wink.winkaipreviz.entity.Scene;
import ru.wink.winkaipreviz.entity.Script;
import ru.wink.winkaipreviz.parser.RuleParser;
import ru.wink.winkaipreviz.repository.SceneRepository;
import ru.wink.winkaipreviz.repository.ScriptRepository;

import java.util.List;

@Service
public class ScriptService {

    private final ScriptRepository scriptRepository;
    private final SceneRepository sceneRepository;
    private final RuleParser ruleParser;

    public ScriptService(ScriptRepository scriptRepository,
                         SceneRepository sceneRepository,
                         RuleParser ruleParser) {
        this.scriptRepository = scriptRepository;
        this.sceneRepository = sceneRepository;
        this.ruleParser = ruleParser;
    }

    @Transactional
    public Script uploadAndParseScript(String text, Script script) {
        // Сохраняем сам сценарий
        Script savedScript = scriptRepository.save(script);

        // Парсим сцены
        List<Scene> scenes = ruleParser.parse(text);

        // Присваиваем ссылку на сценарий каждой сцене
        for (Scene scene : scenes) {
            scene.setScript(savedScript);
        }

        // Сохраняем все сцены в базу
        sceneRepository.saveAll(scenes);

        // Можно вернуть сценарий со списком сцен
        savedScript.setScenes(scenes);
        return savedScript;
    }
}

