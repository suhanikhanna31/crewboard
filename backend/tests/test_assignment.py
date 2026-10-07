from types import SimpleNamespace as NS

from app.assignment import best_match, score


def res(name, skills, zone="A", kind="worker", battery=100.0):
    return NS(name=name, skill_list=skills.split(","), zone=zone, kind=kind, battery=battery)


def task(skill="rebar", zone="A"):
    return NS(required_skill=skill, zone=zone)


def test_skill_required():
    assert score(res("a", "drywall"), task("rebar")) is None


def test_zone_match_preferred():
    near, far = res("near", "rebar", "A"), res("far", "rebar", "B")
    assert best_match([far, near], task("rebar", "A")).name == "near"


def test_low_battery_robot_skipped():
    assert best_match([res("bot", "rebar", kind="robot", battery=10)], task()) is None


def test_load_penalty():
    assert score(res("a", "rebar"), task(), load=2) < score(res("a", "rebar"), task(), load=0)
