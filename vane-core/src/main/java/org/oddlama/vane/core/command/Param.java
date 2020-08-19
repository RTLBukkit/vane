package org.oddlama.vane.core.command;

import static org.oddlama.vane.util.Util.append;

import org.oddlama.vane.core.command.check.CheckResult;
import org.oddlama.vane.core.command.check.ErrorCheckResult;
import org.oddlama.vane.core.command.check.CombinedErrorCheckResult;
import org.oddlama.vane.core.command.params.AnyParam;
import org.oddlama.vane.core.command.params.ChoiceParam;
import org.oddlama.vane.core.command.params.DynamicChoiceParam;
import org.oddlama.vane.core.command.params.FixedParam;
import org.oddlama.vane.core.command.params.SentinelExecutorParam;

import java.util.Optional;
import org.bukkit.command.CommandSender;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.oddlama.vane.core.functional.Function1;
import org.oddlama.vane.core.functional.Function2;
import org.oddlama.vane.core.functional.Function3;
import org.oddlama.vane.core.functional.Function4;
import org.oddlama.vane.core.functional.Function5;
import org.oddlama.vane.core.functional.Function6;

public interface Param {
	public List<Param> params = new ArrayList<>();

	default public void add_param(Param param) {
		params.add(param);
	}

	default public <T1> void exec(Function1<T1, Boolean> f) {
		add_param(new SentinelExecutorParam<>(get_command(), f));
	}

	default public <T1, T2> void exec(Function2<T1, T2, Boolean> f) {
		add_param(new SentinelExecutorParam<>(get_command(), f));
	}

	default public <T1, T2, T3> void exec(Function3<T1, T2, T3, Boolean> f) {
		add_param(new SentinelExecutorParam<>(get_command(), f));
	}

	default public <T1, T2, T3, T4> void exec(Function4<T1, T2, T3, T4, Boolean> f) {
		add_param(new SentinelExecutorParam<>(get_command(), f));
	}

	default public <T1, T2, T3, T4, T5> void exec(Function5<T1, T2, T3, T4, T5, Boolean> f) {
		add_param(new SentinelExecutorParam<>(get_command(), f));
	}

	default public <T1, T2, T3, T4, T5, T6> void exec(Function6<T1, T2, T3, T4, T5, T6, Boolean> f) {
		add_param(new SentinelExecutorParam<>(get_command(), f));
	}

	default public Param any_string() {
		return any(str -> str);
	}

	default public <T> Param any(Function1<String, ? extends T> from_string) {
		final var p = new AnyParam<>(get_command(), from_string);
		add_param(p);
		return p;
	}

	default public Param fixed(String fixed) {
		return fixed(fixed, str -> str);
	}

	default public <T> Param fixed(T fixed, Function1<T, String> to_string) {
		final var p = new FixedParam<>(get_command(), fixed, to_string);
		add_param(p);
		return p;
	}

	default public Param choice(Collection<String> choices) {
		return choice(choices, str -> str);
	}

	default public <T> Param choice(Collection<? extends T> choices,
	                                Function1<T, String> to_string) {
		final var p = new ChoiceParam<>(get_command(), choices, to_string);
		add_param(p);
		return p;
	}

	default public <T> Param choice(Supplier<Collection<? extends T>> choices,
	                                Function1<T, String> to_string,
	                                Function1<String, ? extends T> from_string) {
		final var p = new DynamicChoiceParam<>(get_command(), choices, to_string, from_string);
		add_param(p);
		return p;
	}

	default public Param choose_module() {
		return choice(() -> get_command().module.core.get_modules(),
				m -> m.get_name(),
				m -> get_command().module.core.get_modules().stream()
					.filter(k -> k.get_name().equalsIgnoreCase(m))
					.findFirst()
					.orElse(null));
	}

	default public Param choose_online_player() {
		return choice(() -> get_command().module.getServer().getOnlinePlayers(),
				p -> p.getName(),
				p -> get_command().module.getServer().getOnlinePlayers().stream()
					.filter(k -> k.getName().equals(p))
					.findFirst()
					.orElse(null));
	}

	public default CheckResult check_accept(String[] args, int offset) {
		var results = params.stream()
			.map(p -> p.check_accept(args, offset + 1))
			.collect(Collectors.toList());

		// Return first executor result, if any
		for (var r : results) {
			if (r.good()) {
				return r;
			}
		}

		// Otherwise, combine errors into new error
		return new CombinedErrorCheckResult(results.stream()
			.map(ErrorCheckResult.class::cast)
			.collect(Collectors.toList()));
	}

	public Command get_command();
}
