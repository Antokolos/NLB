#ifndef _ADAPTER_H
#define _ADAPTER_H

void log(const char* messageTemplate, ...);

bool checkInitFunc();

void initFunc();

void setAchievementFunc(const char* achievementName);

void storeFunc();

void clearAchievementFunc(const char* achievementName);

void resetAllFunc();

#endif